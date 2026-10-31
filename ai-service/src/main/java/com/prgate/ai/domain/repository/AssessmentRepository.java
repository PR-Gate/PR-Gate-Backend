package com.prgate.ai.domain.repository;
import java.util.Optional;
import com.prgate.ai.domain.model.aggregates.Assessment;
import com.prgate.ai.domain.model.valueobjects.AssessmentId;
/** Implementations must protect concurrent aggregate updates. */
public interface AssessmentRepository { Optional<Assessment> findById(AssessmentId id); void save(Assessment aggregate); }