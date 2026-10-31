package com.prgate.cicd.domain.model.enums;

public enum PipelineConclusion {
    SUCCESS,
    FAILURE,
    CANCELLED,
    SKIPPED,
    TIMED_OUT,
    ACTION_REQUIRED,
    NEUTRAL,
    STALE
}
