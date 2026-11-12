package com.prgate.metrics.domain.model.valueobjects;
import com.prgate.metrics.domain.exception.DomainRuleViolation;
public final class Checks {
    private Checks() {}
    public static void require(boolean condition, String message) {
        if (!condition) throw new DomainRuleViolation(message);
    }
    public static <T> T required(T value) {
        require(value != null, "required value is missing");
        return value;
    }
    public static String text(String value) {
        require(value != null && !value.isBlank(), "text must not be blank");
        return value.strip();
    }
}
