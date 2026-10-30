package com.prgate.cicd.application.model;

/** Lifecycle actions accepted from GitHub's workflow_run webhook. */
public enum WorkflowRunAction {
    REQUESTED,
    IN_PROGRESS,
    COMPLETED
}
