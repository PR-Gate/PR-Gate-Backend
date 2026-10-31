package com.prgate.cicd.application.service;

import com.prgate.cicd.application.model.ConfigureRepositoryEvaluationCommand;
import com.prgate.cicd.application.port.in.ProvisionEvaluationRepositoryUseCase;
import com.prgate.cicd.application.port.in.RegisterEvaluationRepositoryUseCase;
import com.prgate.cicd.domain.model.aggregates.EvaluationRepository;
import com.prgate.cicd.domain.model.enums.RepositoryProvisioningStatus;
import com.prgate.cicd.domain.model.valueobject.*;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ConfigureRepositoryEvaluationServiceTest {
    private final RegisterEvaluationRepositoryUseCase registration = mock(RegisterEvaluationRepositoryUseCase.class);
    private final ProvisionEvaluationRepositoryUseCase provisioning = mock(ProvisionEvaluationRepositoryUseCase.class);
    private final ConfigureRepositoryEvaluationService service = new ConfigureRepositoryEvaluationService(registration, provisioning);
    private final UUID projectId = UUID.randomUUID();
    private final GitHubRepository github = new GitHubRepository(123, "grupo", "backend");
    private final ConfigureRepositoryEvaluationCommand command = new ConfigureRepositoryEvaluationCommand(
            projectId, github.id(), github.owner(), github.name(), "develop");

    @Test
    void provisionsOnlyAfterEnrollmentAndPreservesExternalProjectReference() {
        var repository = EvaluationRepository.register(EvaluationRepositoryId.generate(),
                new AcademicProjectId(projectId), github, "develop", Instant.now());
        when(registration.register(new AcademicProjectId(projectId), github, "develop")).thenReturn(repository);
        when(provisioning.provision(repository.getId())).thenReturn(RepositoryProvisioningStatus.READY);
        var result = service.configure(command);
        assertEquals(repository.getId(), result.evaluationRepositoryId());
        assertEquals(RepositoryProvisioningStatus.READY, result.status());
        var order = inOrder(registration, provisioning);
        order.verify(registration).register(new AcademicProjectId(projectId), github, "develop");
        order.verify(provisioning).provision(repository.getId());
    }

    @Test
    void associationConflictStopsRemoteProvisioning() {
        when(registration.register(any(), any(), any())).thenThrow(new IllegalStateException("Association conflict"));
        assertThrows(IllegalStateException.class, () -> service.configure(command));
        verifyNoInteractions(provisioning);
    }
}
