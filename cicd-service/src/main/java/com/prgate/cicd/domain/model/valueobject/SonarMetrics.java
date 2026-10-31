package com.prgate.cicd.domain.model.valueobject;

import java.math.BigDecimal;
import java.util.Objects;

/** Metrics reported by SonarQube for one analysis; thresholds remain external policy. */
public record SonarMetrics(BigDecimal coveragePercent, Integer vulnerabilities,
                           BigDecimal duplicatedLinesPercent, BigDecimal technicalDebtPercent) {

    public SonarMetrics {
        coveragePercent = normalize(coveragePercent);
        duplicatedLinesPercent = normalize(duplicatedLinesPercent);
        technicalDebtPercent = normalize(technicalDebtPercent);
        requirePercentage(coveragePercent, "Coverage");
        requirePercentage(duplicatedLinesPercent, "Duplicated lines");
        requirePercentage(technicalDebtPercent, "Technical debt");
        if (vulnerabilities != null && vulnerabilities < 0) {
            throw new IllegalArgumentException("Vulnerability count must not be negative");
        }
    }

    private static BigDecimal normalize(BigDecimal value) {
        return value == null ? null : value.stripTrailingZeros();
    }

    private static void requirePercentage(BigDecimal value, String label) {
        if (value == null) { return; }
        if (value.compareTo(BigDecimal.ZERO) < 0 || value.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new IllegalArgumentException(label + " percentage must be between 0 and 100");
        }
    }
}
