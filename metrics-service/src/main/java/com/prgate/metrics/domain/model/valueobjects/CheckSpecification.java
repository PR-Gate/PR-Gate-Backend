package com.prgate.metrics.domain.model.valueobjects;
public sealed interface CheckSpecification permits RuleCheck, MetricCheck, ManualCheck {}
