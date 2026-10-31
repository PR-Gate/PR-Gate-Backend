package com.prgate.cicd.infrastructure.config;

import com.prgate.cicd.application.model.GitHubWorkflowRunEvent;
import com.prgate.cicd.application.port.in.RecordGitHubWorkflowRunUseCase;
import com.prgate.cicd.application.port.out.PipelineRunRepository;
import com.prgate.cicd.application.port.out.WebhookDeliveryRepository;
import com.prgate.cicd.application.service.RecordGitHubWorkflowRunService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.util.Objects;

@Configuration
public class UseCaseConfiguration {

    @Bean
    com.prgate.cicd.application.port.in.ProvisionEvaluationRepositoryUseCase provisionEvaluationRepositoryUseCase(
            com.prgate.cicd.application.port.out.RepositoryProvisioningStatePort state,
            com.prgate.cicd.application.port.out.SonarProjectProvisioningPort sonar, Clock clock,
            PlatformTransactionManager transactionManager) {
        var service = new com.prgate.cicd.application.service.ProvisionEvaluationRepositoryService(state, sonar, clock);
        var transaction = new TransactionTemplate(transactionManager);
        transaction.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_NOT_SUPPORTED);
        return id -> Objects.requireNonNull(transaction.execute(status -> service.provision(id)));
    }

    @Bean
    com.prgate.cicd.application.port.in.RegisterEvaluationRepositoryUseCase registerEvaluationRepositoryUseCase(
            com.prgate.cicd.application.port.out.EvaluationRepositoryRegistrationPort registrationPort, Clock clock) {
        return new com.prgate.cicd.application.service.RegisterEvaluationRepositoryService(registrationPort, clock);
    }

    @Bean
    com.prgate.cicd.application.port.in.QueryPipelineRunsUseCase queryPipelineRunsUseCase(
            com.prgate.cicd.application.port.out.PipelineRunQueryPort queryPort) {
        return new com.prgate.cicd.application.service.QueryPipelineRunsService(queryPort);
    }

    @Bean
    Clock systemClock() {
        return Clock.systemUTC();
    }

    @Bean
    RecordGitHubWorkflowRunUseCase recordGitHubWorkflowRunUseCase(
            PipelineRunRepository pipelineRunRepository,
            WebhookDeliveryRepository webhookDeliveryRepository,
            Clock clock,
            PlatformTransactionManager transactionManager,
            @Value("${app.github.evaluation-workflow-name:PRGate Evaluation}") String workflowName) {
        RecordGitHubWorkflowRunService service = new RecordGitHubWorkflowRunService(
                pipelineRunRepository, webhookDeliveryRepository, clock, workflowName);
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        return event -> Objects.requireNonNull(
                transactionTemplate.execute(status -> service.record(event)),
                "Workflow run processing returned no result");
    }
}
