package com.prgate.metrics.domain.model.valueobjects;
import java.math.BigDecimal;
import java.util.Optional;
public record MetricObservation(CheckId checkId, String metricKey, Optional<BigDecimal> value,
        boolean evidenceComplete) implements CheckObservation {
    public MetricObservation {
        Checks.required(checkId); metricKey = Checks.text(metricKey);
        value = Checks.required(value).map(BigDecimal::stripTrailingZeros);
    }
}
