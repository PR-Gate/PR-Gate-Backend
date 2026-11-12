package com.prgate.metrics.domain.model.valueobjects;
import com.prgate.metrics.domain.model.enums.ComparisonOperator;
import java.math.BigDecimal;
public record MetricCheck(String metricKey, ComparisonOperator operator, BigDecimal threshold)
        implements CheckSpecification {
    public MetricCheck {
        metricKey = Checks.text(metricKey); Checks.required(operator);
        threshold = Checks.required(threshold).stripTrailingZeros();
    }
    public boolean accepts(BigDecimal observed) { return operator.matches(Checks.required(observed), threshold); }
}
