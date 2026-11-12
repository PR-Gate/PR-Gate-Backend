package com.prgate.metrics.domain.repository;
import com.prgate.metrics.domain.model.aggregates.RubricEvaluationConfiguration;
import com.prgate.metrics.domain.model.valueobjects.*;
import java.util.Optional;
public interface RubricEvaluationConfigurationRepository {
    Optional<RubricEvaluationConfiguration> findById(ConfigurationId id);
    void save(RubricEvaluationConfiguration aggregate);
}
