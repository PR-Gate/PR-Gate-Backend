package com.prgate.cicd.application.service;

import com.prgate.cicd.application.model.ConfigureRepositoryEvaluationCommand;
import com.prgate.cicd.application.port.in.ConfigureRepositoryEvaluationUseCase;
import com.prgate.cicd.application.port.in.ProvisionEvaluationRepositoryUseCase;
import com.prgate.cicd.application.port.in.RegisterEvaluationRepositoryUseCase;
import com.prgate.cicd.domain.model.valueobject.AcademicProjectId;
import com.prgate.cicd.domain.model.valueobject.GitHubRepository;
import java.util.Objects;

public final class ConfigureRepositoryEvaluationService implements ConfigureRepositoryEvaluationUseCase {
    private final RegisterEvaluationRepositoryUseCase registration;
    private final ProvisionEvaluationRepositoryUseCase provisioning;

    public ConfigureRepositoryEvaluationService(RegisterEvaluationRepositoryUseCase registration,
                                                 ProvisionEvaluationRepositoryUseCase provisioning) {
        this.registration = Objects.requireNonNull(registration);
        this.provisioning = Objects.requireNonNull(provisioning);
    }

    @Override
    public Result configure(ConfigureRepositoryEvaluationCommand command) {
        Objects.requireNonNull(command, "Projects repository association is required");
        var repository = registration.register(new AcademicProjectId(command.projectId()),
                new GitHubRepository(command.githubRepositoryId(), command.githubOwner(), command.githubName()),
                command.analysisBranch());
        // Registration commits first. A remote failure leaves a durable enrollment for retry.
        var status = provisioning.provision(repository.getId());
        return new Result(repository.getId(), status);
    }
}
