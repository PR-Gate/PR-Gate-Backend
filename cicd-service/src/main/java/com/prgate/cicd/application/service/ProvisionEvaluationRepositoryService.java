package com.prgate.cicd.application.service;

import com.prgate.cicd.application.port.in.ProvisionEvaluationRepositoryUseCase;
import com.prgate.cicd.application.port.out.RepositoryProvisioningStatePort;
import com.prgate.cicd.application.port.out.SonarProjectProvisioningPort;
import com.prgate.cicd.domain.model.enums.RepositoryProvisioningStatus;
import com.prgate.cicd.domain.model.valueobject.EvaluationRepositoryId;
import java.time.Clock;
import java.util.NoSuchElementException;
import java.util.Objects;

/** Each state write commits independently; remote HTTP calls hold no database transaction. */
public final class ProvisionEvaluationRepositoryService implements ProvisionEvaluationRepositoryUseCase {
    private final RepositoryProvisioningStatePort state;
    private final SonarProjectProvisioningPort sonar;
    private final Clock clock;

    public ProvisionEvaluationRepositoryService(RepositoryProvisioningStatePort state,
                                               SonarProjectProvisioningPort sonar, Clock clock) {
        this.state = Objects.requireNonNull(state);
        this.sonar = Objects.requireNonNull(sonar);
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    public RepositoryProvisioningStatus provision(EvaluationRepositoryId id) {
        var repository = state.findById(id).orElseThrow(() -> new NoSuchElementException("Repository not registered"));
        if (repository.getStatus() == RepositoryProvisioningStatus.READY) {
            return RepositoryProvisioningStatus.READY;
        }
        if (repository.getStatus() == RepositoryProvisioningStatus.FAILED) {
            state.changeStatus(id, RepositoryProvisioningStatus.FAILED, RepositoryProvisioningStatus.PENDING, clock.instant());
        }
        try {
            sonar.ensureProjectExists(repository);
        } catch (RuntimeException exception) {
            state.changeStatus(id, RepositoryProvisioningStatus.PENDING, RepositoryProvisioningStatus.FAILED, clock.instant());
            throw exception;
        }
        if (!state.changeStatus(id, RepositoryProvisioningStatus.PENDING, RepositoryProvisioningStatus.READY, clock.instant())) {
            // A concurrent failed attempt must not erase a confirmed remote success.
            state.changeStatus(id, RepositoryProvisioningStatus.FAILED, RepositoryProvisioningStatus.READY, clock.instant());
        }
        return state.findById(id).orElseThrow().getStatus();
    }
}
