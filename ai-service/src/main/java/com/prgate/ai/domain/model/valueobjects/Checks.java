package com.prgate.ai.domain.model.valueobjects;
import com.prgate.ai.domain.exception.DomainRuleViolation;
public final class Checks {
 private Checks() {}
 public static void require(boolean valid,String message) { if(!valid) throw new DomainRuleViolation(message); }
 public static <T> T present(T value) { require(value!=null,"required value is null"); return value; }
 public static String text(String value) { require(value!=null && !value.isBlank(),"text must not be blank"); return value.strip(); }
}