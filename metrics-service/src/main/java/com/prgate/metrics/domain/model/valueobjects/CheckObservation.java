package com.prgate.metrics.domain.model.valueobjects;
public sealed interface CheckObservation permits RuleObservation, MetricObservation {
    CheckId checkId();
}
