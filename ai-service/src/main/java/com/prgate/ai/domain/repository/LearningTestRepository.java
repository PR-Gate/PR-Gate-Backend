package com.prgate.ai.domain.repository;
import java.util.Optional;
import com.prgate.ai.domain.model.aggregates.LearningTest;
import com.prgate.ai.domain.model.valueobjects.LearningTestId;
/** Implementations must protect concurrent aggregate updates. */
public interface LearningTestRepository { Optional<LearningTest> findById(LearningTestId id); void save(LearningTest aggregate); }