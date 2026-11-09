package com.prgate.cicd.application.service;

import com.prgate.cicd.application.model.RecordSonarAnalysisCommand;
import com.prgate.cicd.application.port.in.RecordSonarAnalysisUseCase;
import com.prgate.cicd.application.port.out.EvaluationRepositoryLookupPort;
import com.prgate.cicd.application.port.out.PipelineRunRepository;
import java.util.NoSuchElementException;

public final class RecordSonarAnalysisService implements RecordSonarAnalysisUseCase {
    private final EvaluationRepositoryLookupPort registrations;
    private final PipelineRunRepository runs;
    public RecordSonarAnalysisService(EvaluationRepositoryLookupPort registrations, PipelineRunRepository runs) {
        this.registrations = registrations; this.runs = runs;
    }
    @Override
    public void record(RecordSonarAnalysisCommand command) {
        var repository = registrations.findByGithubRepositoryId(command.githubRepositoryId())
                .orElseThrow(() -> new NoSuchElementException("Evaluation repository not registered"));
        if (!repository.getSonarProjectKey().equals(command.projectKey())
                || !repository.getAnalysisBranch().equals(command.branch())) {
            throw new IllegalArgumentException("Sonar project or analysis branch mismatch");
        }
        var run = runs.findByGitHubIdentity(repository.getGithubRepository(), command.runId(), command.attempt())
                .orElseThrow(() -> new NoSuchElementException("Pipeline has not arrived yet"));
        var reference = run.getProjectReference().orElseThrow();
        if (!reference.evaluationRepositoryId().equals(repository.getId())
                || !reference.projectId().equals(repository.getProjectId())
                || !run.getBranch().equals(command.branch()) || !run.getCommitSha().equals(command.revision())) {
            throw new IllegalArgumentException("Analysis does not match the registered pipeline");
        }
        run.recordSonarAnalysis(command.analysis());
        runs.save(run);
    }
}
