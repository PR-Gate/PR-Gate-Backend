package com.prgate.metrics.application.port.out;
import com.prgate.metrics.application.dto.GradeEstimationEvidence;
public interface GradeEstimatorPort {
    String estimate(GradeEstimationEvidence evidence);
}
