package com.prgate.cicd.infrastructure.pilot;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prgate.cicd.application.model.*;
import com.prgate.cicd.application.port.in.*;
import com.prgate.cicd.application.port.out.PipelineRunQueryPort;
import com.prgate.cicd.domain.model.enums.*;
import com.prgate.cicd.domain.model.valueobject.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;

/** Explicit local fixture: no academic project or real GitHub workflow is created. */
@Component
@Profile("local-pilot")
public class LocalPilotRunner implements ApplicationRunner {
    private static final long REPOSITORY_ID = 9_000_000_000_001L;
    private static final UUID PROJECT_ID = UUID.fromString("c04e8799-8ff9-450a-8c7b-110eb8f784ea");
    private static final Path MANIFEST = Path.of("target", "local-pilot.json");
    private final ConfigureRepositoryEvaluationUseCase configure;
    private final RecordGitHubWorkflowRunUseCase record;
    private final PipelineRunQueryPort query;
    private final ObjectMapper json;
    private final ConfigurableApplicationContext context;
    private final String phase;
    private final String revision;
    private final String workflow;
    private final int waitSeconds;

    public LocalPilotRunner(ConfigureRepositoryEvaluationUseCase configure, RecordGitHubWorkflowRunUseCase record,
            PipelineRunQueryPort query, ObjectMapper json, ConfigurableApplicationContext context,
            @Value("${pilot.phase:prepare}") String phase, @Value("${pilot.revision:}") String revision,
            @Value("${app.github.evaluation-workflow-name}") String workflow,
            @Value("${pilot.wait-seconds:180}") int waitSeconds) {
        this.configure = configure; this.record = record; this.query = query; this.json = json;
        this.context = context; this.phase = phase; this.revision = revision; this.workflow = workflow;
        this.waitSeconds = waitSeconds;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        try {
            if ("verify".equals(phase)) { verify(); }
            else if ("finish".equals(phase)) { finish(); }
            else if ("prepare".equals(phase)) { prepare(); }
            else { throw new IllegalArgumentException("Pilot phase must be prepare, finish or verify"); }
        } finally { context.close(); }
    }

    private void prepare() throws Exception {
        var sha = new GitCommitSha(revision);
        var configured = configure.configure(new ConfigureRepositoryEvaluationCommand(PROJECT_ID, REPOSITORY_ID,
                "prgate-local-pilot", "cicd-service", "local-pilot"));
        if (configured.status() != RepositoryProvisioningStatus.READY) {
            throw new IllegalStateException("Pilot Sonar project is not ready");
        }
        long runId = Instant.now().toEpochMilli();
        Instant now = Instant.now();
        var event = event(runId, sha, now, WorkflowRunAction.IN_PROGRESS, null);
        if (record.record(event) != RecordGitHubWorkflowRunUseCase.Result.RECORDED) {
            throw new IllegalStateException("Pilot pipeline was not registered");
        }
        var run = query.findByRepository(REPOSITORY_ID, "local-pilot", 0, 100).stream()
                .filter(value -> value.getGithubRunId().value() == runId).findFirst().orElseThrow();
        var manifest = new Manifest(true, run.getId().value().toString(), PROJECT_ID.toString(), REPOSITORY_ID,
                runId, 1, "local-pilot", sha.value(), SonarProjectKey.forRepository(configured.evaluationRepositoryId()).value(),
                now.toString(), workflow);
        Files.createDirectories(MANIFEST.getParent());
        json.writerWithDefaultPrettyPrinter().writeValue(MANIFEST.toFile(), manifest);
        System.out.println("LOCAL_PILOT_PREPARED pipeline=" + manifest.pipelineId());
    }

    private void finish() throws Exception {
        var manifest = json.readValue(MANIFEST.toFile(), Manifest.class);
        if (!manifest.synthetic() || manifest.repositoryId() != REPOSITORY_ID
                || !PROJECT_ID.toString().equals(manifest.projectId())) {
            throw new IllegalArgumentException("Not a local pilot manifest");
        }
        var result = record.record(event(manifest.runId(), new GitCommitSha(manifest.revision()),
                Instant.parse(manifest.startedAt()), WorkflowRunAction.COMPLETED, PipelineConclusion.SUCCESS));
        if (result != RecordGitHubWorkflowRunUseCase.Result.RECORDED) { throw new IllegalStateException("Pilot completion rejected"); }
        System.out.println("LOCAL_PILOT_COMPLETED pipeline=" + manifest.pipelineId());
    }

    private void verify() throws Exception {
        var manifest = json.readValue(MANIFEST.toFile(), Manifest.class);
        if (!manifest.synthetic() || !PROJECT_ID.toString().equals(manifest.projectId())
                || manifest.repositoryId() != REPOSITORY_ID || waitSeconds < 1 || waitSeconds > 600) {
            throw new IllegalArgumentException("Invalid local pilot verification parameters");
        }
        Instant deadline = Instant.now().plusSeconds(waitSeconds);
        do {
            var run = query.findById(UUID.fromString(manifest.pipelineId())).orElseThrow();
            var reference = run.getProjectReference().orElseThrow();
            if (!reference.projectId().value().equals(PROJECT_ID)
                    || run.getRepository().id() != REPOSITORY_ID
                    || !run.getCommitSha().value().equals(manifest.revision())) {
                throw new IllegalStateException("Pilot correlation mismatch");
            }
            if (run.getSonarAnalysis().isPresent()) {
                var evidence = json.createObjectNode();
                evidence.put("synthetic", true);
                evidence.put("id", manifest.pipelineId());
                evidence.put("academicProjectId", manifest.projectId());
                evidence.put("commitSha", manifest.revision());
                evidence.put("status", run.getStatus().name());
                evidence.set("sonarAnalysis", json.valueToTree(run.getSonarAnalysis().orElseThrow()));
                json.writerWithDefaultPrettyPrinter().writeValue(Path.of("target", "local-pilot-result.json").toFile(), evidence);
                System.out.println("LOCAL_PILOT_VERIFIED pipeline=" + manifest.pipelineId());
                return;
            }
            Thread.sleep(3000);
        } while (Instant.now().isBefore(deadline));
        throw new IllegalStateException("No Sonar result before pilot timeout; inspect the inbox and webhook delivery");
    }

    private GitHubWorkflowRunEvent event(long runId, GitCommitSha sha, Instant started,
                                         WorkflowRunAction action, PipelineConclusion conclusion) {
        return new GitHubWorkflowRunEvent(new GitHubDeliveryId(UUID.randomUUID()), action,
                new GitHubRepository(REPOSITORY_ID, "prgate-local-pilot", "cicd-service"),
                new GitHubWorkflowRunId(runId), new GitHubRunAttempt(1), workflow, "local-pilot", sha,
                PipelineTrigger.PUSH, URI.create("https://github.com/prgate-local-pilot/cicd-service/actions/runs/" + runId),
                started, started, conclusion, action == WorkflowRunAction.COMPLETED ? Instant.now() : null);
    }

    public record Manifest(boolean synthetic, String pipelineId, String projectId, long repositoryId,
            long runId, int attempt, String branch, String revision, String sonarProjectKey,
            String startedAt, String workflowName) { }
}
