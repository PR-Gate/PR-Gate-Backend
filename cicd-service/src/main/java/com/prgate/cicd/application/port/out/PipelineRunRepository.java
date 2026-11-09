package com.prgate.cicd.application.port.out;

import com.prgate.cicd.domain.model.aggregates.PipelineRun;
import com.prgate.cicd.domain.model.valueobject.GitHubRepository;
import com.prgate.cicd.domain.model.valueobject.GitHubRunAttempt;
import com.prgate.cicd.domain.model.valueobject.GitHubWorkflowRunId;

import java.util.Optional;

public interface PipelineRunRepository {

    Optional<PipelineRun> findByGitHubIdentity(GitHubRepository repository,
                                                GitHubWorkflowRunId runId,
                                                GitHubRunAttempt attempt);

    PipelineRun save(PipelineRun pipelineRun);
}
