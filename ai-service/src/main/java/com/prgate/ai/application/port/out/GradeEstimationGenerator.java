package com.prgate.ai.application.port.out;
import com.prgate.ai.application.dto.*;
import java.util.List;

public interface GradeEstimationGenerator {
    Generation estimate(GradeEstimateRequest request);
    record Generation(String summary,List<GradeEstimateResult.CriterionEstimate> criteria,String provider,String model) { }
}
