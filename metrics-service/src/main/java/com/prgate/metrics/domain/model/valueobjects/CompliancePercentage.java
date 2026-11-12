package com.prgate.metrics.domain.model.valueobjects;
import java.math.BigDecimal;
import java.math.RoundingMode;
public record CompliancePercentage(BigDecimal value) {
    public CompliancePercentage {
        Checks.required(value);
        Checks.require(value.signum() >= 0 && value.compareTo(new BigDecimal("100")) <= 0,
                "percentage must be between zero and 100");
        value = value.setScale(2, RoundingMode.HALF_UP);
    }
    public static CompliancePercentage ratio(BigDecimal numerator, BigDecimal denominator) {
        Checks.required(numerator); Checks.required(denominator);
        Checks.require(denominator.signum() > 0, "percentage denominator must be positive");
        return new CompliancePercentage(numerator.multiply(new BigDecimal("100"))
                .divide(denominator, 8, RoundingMode.HALF_UP));
    }
}
