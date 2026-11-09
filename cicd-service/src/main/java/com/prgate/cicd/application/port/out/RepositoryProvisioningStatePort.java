package com.prgate.cicd.application.port.out;

import com.prgate.cicd.domain.model.aggregates.EvaluationRepository;
import com.prgate.cicd.domain.model.valueobject.EvaluationRepositoryId;
import com.prgate.cicd.domain.model.enums.RepositoryProvisioningStatus;
import java.time.Instant;
import java.util.Optional;

public interface RepositoryProvisioningStatePort {
    Optional<EvaluationRepository> findById(EvaluationRepositoryId id);
    boolean changeStatus(EvaluationRepositoryId id, RepositoryProvisioningStatus expected,
                         RepositoryProvisioningStatus target, Instant now);
}
