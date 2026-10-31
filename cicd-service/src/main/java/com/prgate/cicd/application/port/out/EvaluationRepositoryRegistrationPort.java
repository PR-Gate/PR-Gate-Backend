package com.prgate.cicd.application.port.out;

import com.prgate.cicd.domain.model.aggregates.EvaluationRepository;

public interface EvaluationRepositoryRegistrationPort {
    /** Atomically inserts the registration or returns the existing registration for the GitHub id. */
    EvaluationRepository registerIfAbsent(EvaluationRepository repository);
}
