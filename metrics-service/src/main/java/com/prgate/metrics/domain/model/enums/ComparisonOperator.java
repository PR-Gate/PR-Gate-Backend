package com.prgate.metrics.domain.model.enums;
import java.math.BigDecimal;
public enum ComparisonOperator {
    LT, LTE, EQ, GTE, GT;
    public boolean matches(BigDecimal observed, BigDecimal threshold) {
        int comparison = observed.compareTo(threshold);
        return switch (this) {
            case LT -> comparison < 0; case LTE -> comparison <= 0; case EQ -> comparison == 0;
            case GTE -> comparison >= 0; case GT -> comparison > 0;
        };
    }
}
