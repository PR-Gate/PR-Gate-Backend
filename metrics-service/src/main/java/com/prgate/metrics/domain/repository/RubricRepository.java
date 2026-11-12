package com.prgate.metrics.domain.repository;
import com.prgate.metrics.domain.model.aggregates.Rubric;
import com.prgate.metrics.domain.model.valueobjects.*;
import java.util.Optional;
public interface RubricRepository {
    Optional<Rubric> findById(RubricId id, RubricVersion version);
    void save(Rubric aggregate);
}
