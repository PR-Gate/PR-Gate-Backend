package com.prgate.cicd.infrastructure.pilot;

import com.prgate.cicd.application.model.ConfigureRepositoryEvaluationCommand;
import com.prgate.cicd.application.port.in.ConfigureRepositoryEvaluationUseCase;
import com.prgate.cicd.domain.model.enums.RepositoryProvisioningStatus;
import com.prgate.cicd.domain.model.valueobject.EvaluationRepositoryId;
import org.junit.jupiter.api.Test;
import org.springframework.context.ConfigurableApplicationContext;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class RepositoryPilotRunnerTest {
    @Test
    void registersRealRepositoryReferencesAndClosesSetupProcess() {
        var configure = mock(ConfigureRepositoryEvaluationUseCase.class);
        var context = mock(ConfigurableApplicationContext.class);
        UUID project = UUID.randomUUID();
        var command = new ConfigureRepositoryEvaluationCommand(project,1432721468L,"PR-Gate","PR-Gate-Backend","feat/ci-cd-service");
        when(configure.configure(command)).thenReturn(new ConfigureRepositoryEvaluationUseCase.Result(
                EvaluationRepositoryId.generate(), RepositoryProvisioningStatus.READY));
        new RepositoryPilotRunner(configure,context,project,1432721468L,"PR-Gate","PR-Gate-Backend","feat/ci-cd-service","",null,null).run(null);
        verify(configure).configure(command);
        verify(context).close();
    }

    @Test
    void doesNotReportSuccessOnProvisioningFailure() {
        var configure = mock(ConfigureRepositoryEvaluationUseCase.class);
        var context = mock(ConfigurableApplicationContext.class);
        when(configure.configure(any())).thenThrow(new IllegalStateException("Sonar unavailable"));
        var runner = new RepositoryPilotRunner(configure,context,UUID.randomUUID(),1432721468L,"PR-Gate","PR-Gate-Backend","feat/ci-cd-service","",null,null);
        assertThrows(IllegalStateException.class, () -> runner.run(null));
        verify(context).close();
    }
}
