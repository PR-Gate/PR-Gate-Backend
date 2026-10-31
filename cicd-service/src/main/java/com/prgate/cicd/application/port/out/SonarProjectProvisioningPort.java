package com.prgate.cicd.application.port.out;

import com.prgate.cicd.domain.model.aggregates.EvaluationRepository;

public interface SonarProjectProvisioningPort {
    /** Creates the project or verifies that the same stable key already exists. */
    void ensureProjectExists(EvaluationRepository repository);
}
