package com.prgate.metrics.application.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

/** Pass thresholds are translated to Sonar's failure operators without rounding. */
public record QualityGateRequest(int schemaVersion, String eventType, UUID eventId, UUID configurationId,
        UUID projectId, long repositoryId, String organization, String gitRef,
        long policyVersion, Instant occurredAt, List<Condition> conditions) {
    public static QualityGateRequest from(StoredConfiguration configuration, UUID id, long version) {
        return new QualityGateRequest(1, "metrics.quality-gate.requested.v1", id, configuration.id(),
                configuration.projectId(), configuration.repositoryId(), configuration.organization(),
                configuration.gitRef(), version, Instant.now(), conditions(configuration));
    }
    public static List<Condition> conditions(StoredConfiguration configuration) {
        Map<String, Condition> result = new TreeMap<>();
        for (var check : configuration.checks()) {
            if (!check.type().equals("METRIC")) continue;
            String op = switch (check.operator()) {
                case GTE -> "LT";
                case LTE -> "GT";
                default -> throw new IllegalArgumentException("Quality Gate pass conditions require GTE or LTE");
            };
            var condition = new Condition(check.key(), op, check.threshold().stripTrailingZeros());
            var previous = result.putIfAbsent(condition.metric(), condition);
            if (previous != null && !previous.equals(condition))
                throw new IllegalArgumentException("Conflicting Quality Gate conditions for the same metric");
        }
        return List.copyOf(result.values());
    }
    public record Condition(String metric, String op, BigDecimal error) { }
}
