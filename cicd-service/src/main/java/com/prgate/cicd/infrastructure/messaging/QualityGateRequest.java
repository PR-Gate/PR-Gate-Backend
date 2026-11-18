package com.prgate.cicd.infrastructure.messaging;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

public record QualityGateRequest(int schemaVersion, String eventType, UUID eventId, UUID configurationId,
        UUID projectId, long repositoryId, String organization, String gitRef,
        long policyVersion, Instant occurredAt, List<Condition> conditions) {
    public QualityGateRequest {
        if (schemaVersion!=1 || !"metrics.quality-gate.requested.v1".equals(eventType)
                || repositoryId<=0 || policyVersion<=0 || organization==null || organization.isBlank()
                || gitRef==null || !gitRef.matches("refs/heads/[^\\s]+")) throw new IllegalArgumentException("Invalid Quality Gate request");
        Objects.requireNonNull(eventId); Objects.requireNonNull(configurationId); Objects.requireNonNull(projectId);
        Objects.requireNonNull(occurredAt);
        conditions=List.copyOf(conditions);
        if (conditions.size()>8 || conditions.stream().map(Condition::metric).distinct().count()!=conditions.size())
            throw new IllegalArgumentException("Invalid Quality Gate conditions");
    }
    public record Condition(String metric, String op, BigDecimal error) {
        public Condition {
            if (!Set.of("coverage","duplicated_lines_density","ncloc","bugs","vulnerabilities",
                    "code_smells","sqale_index","sqale_debt_ratio").contains(metric)
                    || !Set.of("LT","GT").contains(op) || error==null || error.signum()<0)
                throw new IllegalArgumentException("Unsupported Quality Gate condition");
        }
    }
}
