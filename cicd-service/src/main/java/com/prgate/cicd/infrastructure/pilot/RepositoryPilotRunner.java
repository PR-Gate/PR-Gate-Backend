package com.prgate.cicd.infrastructure.pilot;

import com.prgate.cicd.application.model.ConfigureRepositoryEvaluationCommand;
import com.prgate.cicd.application.port.in.ConfigureRepositoryEvaluationUseCase;
import com.prgate.cicd.application.port.in.RegisterEvaluationRepositoryUseCase;
import com.prgate.cicd.domain.model.valueobject.AcademicProjectId;
import com.prgate.cicd.domain.model.valueobject.GitHubRepository;
import com.prgate.cicd.infrastructure.sonar.SonarProjectHttpAdapter;
import com.prgate.cicd.domain.model.enums.RepositoryProvisioningStatus;
import com.prgate.cicd.domain.model.valueobject.SonarProjectKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Explicit technical pilot; does not create or simulate workflow executions. */
@Component
@Profile("repository-pilot")
public class RepositoryPilotRunner implements ApplicationRunner {
    private final ConfigureRepositoryEvaluationUseCase configure;
    private final ConfigurableApplicationContext context;
    private final ConfigureRepositoryEvaluationCommand command;
    private final String existingKey;
    private final RegisterEvaluationRepositoryUseCase registration;
    private final SonarProjectHttpAdapter sonar;

    public RepositoryPilotRunner(ConfigureRepositoryEvaluationUseCase configure,
            ConfigurableApplicationContext context,
            @Value("${pilot.project-id}") UUID projectId,
            @Value("${pilot.repository-id}") long repositoryId,
            @Value("${pilot.owner}") String owner,
            @Value("${pilot.repository}") String repository,
            @Value("${pilot.branch}") String branch,
            @Value("${pilot.existing-sonar-key:}") String existingKey,
            RegisterEvaluationRepositoryUseCase registration, SonarProjectHttpAdapter sonar) {
        this.configure = configure;
        this.context = context;
        this.command = new ConfigureRepositoryEvaluationCommand(projectId, repositoryId, owner, repository, branch);
        this.existingKey = existingKey;
        this.registration = registration;
        this.sonar = sonar;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        try {
            if (!existingKey.isBlank()) {
                var key = new SonarProjectKey(existingKey);
                sonar.requireExistingProject(key.value());
                var registered = registration.registerExisting(new AcademicProjectId(command.projectId()),
                        new GitHubRepository(command.githubRepositoryId(), command.githubOwner(), command.githubName()),
                        command.analysisBranch(), key);
                if (registered.getStatus() != RepositoryProvisioningStatus.READY) {
                    throw new IllegalStateException("Existing repository registration is not ready");
                }
                System.out.println("REPOSITORY_PILOT_READY repository=" + registered.getGithubRepository().fullName());
                System.out.println("SONAR_PROJECT_KEY=" + registered.getSonarProjectKey().value());
                return;
            }
            var result = configure.configure(command);
            if (result.status() != RepositoryProvisioningStatus.READY) {
                throw new IllegalStateException("Repository pilot provisioning did not complete");
            }
            System.out.println("REPOSITORY_PILOT_READY repository=" + command.githubOwner() + "/" + command.githubName());
            System.out.println("SONAR_PROJECT_KEY=" + SonarProjectKey.forRepository(result.evaluationRepositoryId()).value());
            System.out.println("ANALYSIS_BRANCH=" + command.analysisBranch());
            System.out.println("Pilot setup only: no GitHub workflow run has been recorded.");
        } finally {
            context.close();
        }
    }
}
