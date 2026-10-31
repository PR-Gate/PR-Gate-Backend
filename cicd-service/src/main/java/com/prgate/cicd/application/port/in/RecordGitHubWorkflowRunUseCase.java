package com.prgate.cicd.application.port.in;

import com.prgate.cicd.application.model.GitHubWorkflowRunEvent;

public interface RecordGitHubWorkflowRunUseCase {

    Result record(GitHubWorkflowRunEvent event);

    enum Result {
        RECORDED,
        DUPLICATE_DELIVERY,
        IGNORED_WORKFLOW,
        IGNORED_UNREGISTERED_REPOSITORY
    }
}
