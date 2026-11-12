package com.prgate.metrics.domain.repository;
import com.prgate.metrics.domain.model.aggregates.SubmissionEvaluation;
import com.prgate.metrics.domain.model.valueobjects.*;
import java.util.Optional;
public interface SubmissionEvaluationRepository {
    Optional<SubmissionEvaluation> findById(EvaluationId id);
    void save(SubmissionEvaluation aggregate);
}
