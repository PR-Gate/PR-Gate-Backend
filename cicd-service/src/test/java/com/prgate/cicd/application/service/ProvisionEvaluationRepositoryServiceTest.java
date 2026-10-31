package com.prgate.cicd.application.service;

import com.prgate.cicd.application.port.out.RepositoryProvisioningStatePort;
import com.prgate.cicd.application.port.out.SonarProjectProvisioningPort;
import com.prgate.cicd.domain.model.aggregates.EvaluationRepository;
import com.prgate.cicd.domain.model.enums.RepositoryProvisioningStatus;
import com.prgate.cicd.domain.model.valueobject.*;
import org.junit.jupiter.api.Test;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProvisionEvaluationRepositoryServiceTest {
    private final RepositoryProvisioningStatePort state = mock(RepositoryProvisioningStatePort.class);
    private final SonarProjectProvisioningPort sonar = mock(SonarProjectProvisioningPort.class);
    private final Instant now = Instant.parse("2026-10-30T12:00:00Z");
    private final EvaluationRepository repository = EvaluationRepository.register(
            EvaluationRepositoryId.generate(), new AcademicProjectId(UUID.randomUUID()),
            new GitHubRepository(123, "team", "backend"), "main", now);
    private final ProvisionEvaluationRepositoryService service = new ProvisionEvaluationRepositoryService(
            state, sonar, Clock.fixed(now, ZoneOffset.UTC));

    @Test
    void alreadyReadyDoesNotCallSonar() {
        repository.markProvisioned(now);
        when(state.findById(repository.getId())).thenReturn(Optional.of(repository));
        assertEquals(RepositoryProvisioningStatus.READY, service.provision(repository.getId()));
        verifyNoInteractions(sonar);
    }

    @Test
    void remoteFailureRecordsFailedAndPropagatesError() {
        when(state.findById(repository.getId())).thenReturn(Optional.of(repository));
        doThrow(new IllegalStateException("Unavailable")).when(sonar).ensureProjectExists(repository);
        assertThrows(IllegalStateException.class, () -> service.provision(repository.getId()));
        verify(state).changeStatus(repository.getId(), RepositoryProvisioningStatus.PENDING,
                RepositoryProvisioningStatus.FAILED, now);
        verify(state, never()).changeStatus(any(), any(), eq(RepositoryProvisioningStatus.READY), any());
    }

    @Test
    void confirmedRemoteSuccessWinsOverConcurrentFailure() {
        when(state.findById(repository.getId())).thenReturn(Optional.of(repository));
        when(state.changeStatus(repository.getId(), RepositoryProvisioningStatus.FAILED,
                RepositoryProvisioningStatus.READY, now)).thenAnswer(call -> {
                    repository.markProvisioned(now);
                    return true;
                });
        assertEquals(RepositoryProvisioningStatus.READY, service.provision(repository.getId()));
        verify(sonar).ensureProjectExists(repository);
        verify(state).changeStatus(repository.getId(), RepositoryProvisioningStatus.FAILED,
                RepositoryProvisioningStatus.READY, now);
    }
}
