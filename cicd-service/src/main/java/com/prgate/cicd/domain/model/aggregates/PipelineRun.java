package com.prgate.cicd.domain.model.aggregates;

import com.prgate.cicd.domain.model.enums.PipelineConclusion;
import com.prgate.cicd.domain.model.enums.PipelineRunStatus;
import com.prgate.cicd.domain.model.enums.PipelineTrigger;
import com.prgate.cicd.domain.model.valueobject.GitCommitSha;
import com.prgate.cicd.domain.model.valueobject.GitHubRepository;
import com.prgate.cicd.domain.model.valueobject.GitHubRunAttempt;
import com.prgate.cicd.domain.model.valueobject.GitHubWorkflowRunId;
import com.prgate.cicd.domain.model.valueobject.PipelineRunId;
import com.prgate.cicd.domain.model.valueobject.SonarAnalysisResult;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/** Aggregate for one run of the repository's dedicated CI/CD evaluation workflow. */
public final class PipelineRun {

    private final PipelineRunId id;
    private final GitHubWorkflowRunId githubRunId;
    private final GitHubRunAttempt githubRunAttempt;
    private final GitHubRepository repository;
    private final String workflowName;
    private final String branch;
    private final GitCommitSha commitSha;
    private final PipelineTrigger trigger;
    private final URI runUrl;
    private final Instant createdAt;
    private PipelineRunStatus status;
    private PipelineConclusion conclusion;
    private Instant startedAt;
    private Instant completedAt;
    private Instant updatedAt;
    private SonarAnalysisResult sonarAnalysis;
    private com.prgate.cicd.domain.model.valueobject.PipelineProjectReference projectReference;

    public void associateWith(com.prgate.cicd.domain.model.valueobject.PipelineProjectReference reference) {
        Objects.requireNonNull(reference, "Project reference is required");
        if (projectReference != null && !projectReference.equals(reference)) {
            throw new IllegalStateException("A recorded pipeline cannot change its academic association");
        }
        projectReference = reference;
    }

    public Optional<com.prgate.cicd.domain.model.valueobject.PipelineProjectReference> getProjectReference() {
        return Optional.ofNullable(projectReference);
    }

    private PipelineRun(PipelineRunId id, GitHubWorkflowRunId githubRunId, GitHubRunAttempt githubRunAttempt,
                        GitHubRepository repository,
                        String workflowName, String branch, GitCommitSha commitSha, PipelineTrigger trigger,
                        URI runUrl, Instant createdAt) {
        this.id = Objects.requireNonNull(id, "Pipeline run id is required");
        this.githubRunId = Objects.requireNonNull(githubRunId, "GitHub workflow run id is required");
        this.githubRunAttempt = Objects.requireNonNull(githubRunAttempt, "GitHub workflow run attempt is required");
        this.repository = Objects.requireNonNull(repository, "GitHub repository is required");
        this.workflowName = requireText(workflowName, "Workflow name", 200);
        this.branch = requireText(branch, "Branch", 255);
        this.commitSha = Objects.requireNonNull(commitSha, "Commit SHA is required");
        this.trigger = Objects.requireNonNull(trigger, "Pipeline trigger is required");
        this.runUrl = requireGitHubUrl(runUrl);
        this.createdAt = Objects.requireNonNull(createdAt, "Creation time is required");
        this.updatedAt = createdAt;
        this.status = PipelineRunStatus.QUEUED;
    }

    public static PipelineRun queued(PipelineRunId id, GitHubWorkflowRunId githubRunId,
                                      GitHubRunAttempt githubRunAttempt,
                                      GitHubRepository repository, String workflowName, String branch,
                                      GitCommitSha commitSha, PipelineTrigger trigger, URI runUrl,
                                      Instant createdAt) {
        return new PipelineRun(id, githubRunId, githubRunAttempt, repository, workflowName, branch, commitSha,
                trigger, runUrl, createdAt);
    }

    /** Rebuilds an aggregate from persisted state while keeping its invariants. */
    public static PipelineRun reconstitute(PipelineRunId id, GitHubWorkflowRunId githubRunId,
                                           GitHubRunAttempt githubRunAttempt, GitHubRepository repository,
                                           String workflowName, String branch, GitCommitSha commitSha,
                                           PipelineTrigger trigger, URI runUrl, Instant createdAt,
                                           PipelineRunStatus status, PipelineConclusion conclusion,
                                           Instant startedAt, Instant completedAt, Instant updatedAt,
                                           SonarAnalysisResult sonarAnalysis) {
        PipelineRun pipelineRun = new PipelineRun(id, githubRunId, githubRunAttempt, repository,
                workflowName, branch, commitSha, trigger, runUrl, createdAt);
        pipelineRun.status = Objects.requireNonNull(status, "Pipeline run status is required");
        pipelineRun.conclusion = conclusion;
        pipelineRun.startedAt = startedAt;
        pipelineRun.completedAt = completedAt;
        pipelineRun.updatedAt = Objects.requireNonNull(updatedAt, "Update time is required");
        pipelineRun.sonarAnalysis = sonarAnalysis;
        pipelineRun.validatePersistedState();
        return pipelineRun;
    }

    /** Moves a queued run to in-progress. Replayed start events are harmless. */
    public void start(Instant startedAt) {
        Objects.requireNonNull(startedAt, "Start time is required");
        if (status == PipelineRunStatus.IN_PROGRESS) {
            return;
        }
        if (status == PipelineRunStatus.COMPLETED) {
            throw new IllegalStateException("A completed pipeline run cannot be started");
        }
        if (startedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("Start time must not precede creation time");
        }
        this.startedAt = startedAt;
        this.updatedAt = startedAt;
        this.status = PipelineRunStatus.IN_PROGRESS;
    }

    /** Completes a queued or running run. Repeated identical completion events are harmless. */
    public void complete(PipelineConclusion conclusion, Instant completedAt) {
        Objects.requireNonNull(conclusion, "Pipeline conclusion is required");
        Objects.requireNonNull(completedAt, "Completion time is required");
        if (status == PipelineRunStatus.COMPLETED) {
            if (this.conclusion == conclusion) {
                return;
            }
            throw new IllegalStateException("A completed pipeline run cannot change its conclusion");
        }
        if (completedAt.isBefore(updatedAt)) {
            throw new IllegalArgumentException("Completion time must not precede the latest run update");
        }
        this.conclusion = conclusion;
        this.completedAt = completedAt;
        this.updatedAt = completedAt;
        this.status = PipelineRunStatus.COMPLETED;
    }

    /** Stores the single SonarQube analysis associated with this dedicated evaluation run. */
    public void recordSonarAnalysis(SonarAnalysisResult analysis) {
        Objects.requireNonNull(analysis, "SonarQube analysis is required");
        if (sonarAnalysis == null) {
            this.sonarAnalysis = analysis;
            return;
        }
        if (!sonarAnalysis.equals(analysis)) {
            throw new IllegalStateException("A pipeline run cannot be associated with multiple SonarQube analyses");
        }
    }

    public Optional<Duration> getDuration() {
        if (startedAt == null || completedAt == null) {
            return Optional.empty();
        }
        return Optional.of(Duration.between(startedAt, completedAt));
    }

    public PipelineRunId getId() { return id; }
    public GitHubWorkflowRunId getGithubRunId() { return githubRunId; }
    public GitHubRunAttempt getGithubRunAttempt() { return githubRunAttempt; }
    public GitHubRepository getRepository() { return repository; }
    public String getWorkflowName() { return workflowName; }
    public String getBranch() { return branch; }
    public GitCommitSha getCommitSha() { return commitSha; }
    public PipelineTrigger getTrigger() { return trigger; }
    public URI getRunUrl() { return runUrl; }
    public Instant getCreatedAt() { return createdAt; }
    public PipelineRunStatus getStatus() { return status; }
    public Optional<PipelineConclusion> getConclusion() { return Optional.ofNullable(conclusion); }
    public Optional<Instant> getStartedAt() { return Optional.ofNullable(startedAt); }
    public Optional<Instant> getCompletedAt() { return Optional.ofNullable(completedAt); }
    public Instant getUpdatedAt() { return updatedAt; }
    public Optional<SonarAnalysisResult> getSonarAnalysis() { return Optional.ofNullable(sonarAnalysis); }

    private static URI requireGitHubUrl(URI value) {
        Objects.requireNonNull(value, "GitHub workflow URL is required");
        if (!"https".equalsIgnoreCase(value.getScheme()) || !"github.com".equalsIgnoreCase(value.getHost())) {
            throw new IllegalArgumentException("Workflow run URL must be hosted by github.com over HTTPS");
        }
        return value;
    }

    private static String requireText(String value, String label, int maxLength) {
        Objects.requireNonNull(value, label + " is required");
        String normalized = value.strip();
        if (normalized.isEmpty() || normalized.length() > maxLength) {
            throw new IllegalArgumentException(label + " must contain between 1 and " + maxLength + " characters");
        }
        return normalized;
    }

    private void validatePersistedState() {
        if (updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("Update time must not precede creation time");
        }
        if (startedAt != null && startedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("Start time must not precede creation time");
        }
        if (completedAt != null && (completedAt.isBefore(createdAt)
                || (startedAt != null && completedAt.isBefore(startedAt)))) {
            throw new IllegalArgumentException("Completion time must not precede creation or start time");
        }

        switch (status) {
            case QUEUED -> {
                if (startedAt != null || completedAt != null || conclusion != null) {
                    throw new IllegalArgumentException("Queued run cannot have start or completion data");
                }
            }
            case IN_PROGRESS -> {
                if (startedAt == null || completedAt != null || conclusion != null) {
                    throw new IllegalArgumentException("In-progress run must have only start data");
                }
            }
            case COMPLETED -> {
                if (completedAt == null || conclusion == null || updatedAt.isBefore(completedAt)) {
                    throw new IllegalArgumentException("Completed run must have valid completion data");
                }
            }
        }
    }
}
