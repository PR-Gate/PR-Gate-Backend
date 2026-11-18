package com.prgate.metrics.application;
import com.prgate.metrics.application.dto.*;
import com.prgate.metrics.domain.model.valueobjects.*;
import com.prgate.metrics.domain.model.enums.ComparisonOperator;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class QualityGatePolicyTest {
    @Test void passThresholdsBecomeFailureOperatorsWithoutChangingTheirValues() {
        var configuration=config(List.of(check("coverage",ComparisonOperator.GTE,"80"),check("duplicated_lines_density",ComparisonOperator.LTE,"3")));
        var result=QualityGateRequest.conditions(configuration);
        var coverage=result.stream().filter(c->c.metric().equals("coverage")).findFirst().orElseThrow();
        assertEquals("LT",coverage.op()); assertEquals(0,coverage.error().compareTo(BigDecimal.valueOf(80)));
        var duplication=result.stream().filter(c->c.metric().equals("duplicated_lines_density")).findFirst().orElseThrow();
        assertEquals("GT",duplication.op()); assertEquals(0,duplication.error().compareTo(BigDecimal.valueOf(3)));
    }
    @Test void unsupportedComparisonsAndConflictingThresholdsCannotSilentlyChangeMeaning() {
        assertThrows(IllegalArgumentException.class,()->QualityGateRequest.conditions(config(List.of(check("coverage",ComparisonOperator.GT,"80")))));
        assertThrows(IllegalArgumentException.class,()->QualityGateRequest.conditions(config(List.of(
                check("coverage",ComparisonOperator.GTE,"80"),check("coverage",ComparisonOperator.GTE,"90")))));
    }
    private StoredConfiguration.CheckDefinition check(String key,ComparisonOperator operator,String value) {
        return new StoredConfiguration.CheckDefinition(UUID.randomUUID(),UUID.randomUUID(),"METRIC",key,operator,new BigDecimal(value));
    }
    private StoredConfiguration config(List<StoredConfiguration.CheckDefinition> checks) {
        return new StoredConfiguration(UUID.randomUUID(),UUID.randomUUID(),123,"example","refs/heads/main",null,
                UUID.randomUUID(),checks,UUID.randomUUID(),java.time.Instant.now(),"test");
    }
}
