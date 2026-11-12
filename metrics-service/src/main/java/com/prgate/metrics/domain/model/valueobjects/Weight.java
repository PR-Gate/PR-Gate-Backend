package com.prgate.metrics.domain.model.valueobjects;
import java.math.BigDecimal;
public record Weight(BigDecimal value) {
    public Weight {
        Checks.required(value);
        Checks.require(value.signum() > 0 && value.compareTo(new BigDecimal("100")) <= 0,
                "weight must be greater than zero and at most 100");
        value = value.stripTrailingZeros();
    }
}
