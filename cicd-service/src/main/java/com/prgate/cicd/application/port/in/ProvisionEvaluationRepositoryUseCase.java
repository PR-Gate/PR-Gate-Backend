package com.prgate.cicd.application.port.in;

import com.prgate.cicd.domain.model.enums.RepositoryProvisioningStatus;
import com.prgate.cicd.domain.model.valueobject.EvaluationRepositoryId;

public interface ProvisionEvaluationRepositoryUseCase {
    RepositoryProvisioningStatus provision(EvaluationRepositoryId id);
}
