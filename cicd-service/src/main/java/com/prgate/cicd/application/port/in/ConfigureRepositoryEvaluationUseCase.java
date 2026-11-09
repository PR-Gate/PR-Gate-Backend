package com.prgate.cicd.application.port.in;

import com.prgate.cicd.application.model.ConfigureRepositoryEvaluationCommand;
import com.prgate.cicd.domain.model.enums.RepositoryProvisioningStatus;
import com.prgate.cicd.domain.model.valueobject.EvaluationRepositoryId;

/** Entry point for a trusted Projects integration adapter, not an end-user project creation API. */
public interface ConfigureRepositoryEvaluationUseCase {
    Result configure(ConfigureRepositoryEvaluationCommand command);

    record Result(EvaluationRepositoryId evaluationRepositoryId, RepositoryProvisioningStatus status) { }
}
