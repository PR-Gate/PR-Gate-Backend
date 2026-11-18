package com.prgate.metrics.application.port.out;
import com.prgate.metrics.application.dto.AnalysisCompletedEvent;
import java.math.BigDecimal;
import java.util.Map;

public interface SonarEvidencePort {
    Map<String, BigDecimal> collect(AnalysisCompletedEvent event);
    void validateRule(String ruleKey);
    class EvidenceUnavailable extends RuntimeException {
        public EvidenceUnavailable(String reason) { super(reason); }
    }
}
