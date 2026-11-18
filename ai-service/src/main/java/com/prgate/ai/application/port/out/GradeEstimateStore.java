package com.prgate.ai.application.port.out;
import com.prgate.ai.application.dto.GradeEstimateResult;
import java.util.Optional;

public interface GradeEstimateStore {
    Optional<GradeEstimateResult> find(String requestHash);
    GradeEstimateResult saveFirst(String requestHash,GradeEstimateResult result);
}
