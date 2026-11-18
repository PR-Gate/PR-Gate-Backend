package com.prgate.metrics.application.port.out;
import java.util.Optional;
public interface GradeEstimateProjection {
    Optional<String> find(String evidenceHash);
    void save(String evidenceHash,String result);
}
