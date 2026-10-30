package com.prgate.cicd.domain.model.valueobject;

import java.math.BigDecimal;
import java.util.Objects;

/** Metrics reported by SonarQube for one analysis; thresholds remain external policy. */
public record SonarMetrics(BigDecimal coveragePercent, int vulnerabilities,
                           BigDecimal duplicatedLinesPercent, BigDecimal technicalDebtPercent) {

    public SonarMetrics {
        requirePercentage(coveragePercent, "Coverage");
        requirePercentage(duplicatedLinesPercent, "Duplicated lines");
        requirePercentage(technicalDebtPercent, "Technical debt");
        if (vulnerabilities < 0) {
            throw new IllegalArgumentException("Vulnerability count must not be negative");
        }
    }

    private static void requirePercentage(BigDecimal value, String label) {
        Objects.requireNonNull(value, label + " percentage is required");
        if (value.compareTo(BigDecimal.ZERO) < 0 || value.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new IllegalArgumentException(label + " percentage must be between 0 and 100");
        }
    }
}
