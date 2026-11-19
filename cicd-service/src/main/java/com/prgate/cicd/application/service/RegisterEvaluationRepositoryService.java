package com.prgate.cicd.application.service;

import com.prgate.cicd.application.port.in.RegisterEvaluationRepositoryUseCase;
import com.prgate.cicd.application.port.out.EvaluationRepositoryRegistrationPort;
import com.prgate.cicd.domain.model.aggregates.EvaluationRepository;
import com.prgate.cicd.domain.model.valueobject.AcademicProjectId;
import com.prgate.cicd.domain.model.valueobject.EvaluationRepositoryId;
import com.prgate.cicd.domain.model.valueobject.GitHubRepository;
import com.prgate.cicd.domain.model.valueobject.SonarProjectKey;
import java.time.Clock;
import java.util.Objects;

public final class RegisterEvaluationRepositoryService implements RegisterEvaluationRepositoryUseCase {
    private final EvaluationRepositoryRegistrationPort registrationPort;
    private final Clock clock;

    public RegisterEvaluationRepositoryService(EvaluationRepositoryRegistrationPort registrationPort, Clock clock) {
        this.registrationPort = Objects.requireNonNull(registrationPort);
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    public EvaluationRepository register(AcademicProjectId projectId, GitHubRepository repository, String branch) {
        EvaluationRepository candidate = EvaluationRepository.register(
                EvaluationRepositoryId.generate(), projectId, repository, branch, clock.instant());
        return registerCandidate(candidate);
    }

    @Override
    public EvaluationRepository registerExisting(AcademicProjectId projectId, GitHubRepository repository, String branch,
                                                 SonarProjectKey sonarProjectKey) {
        var candidate = EvaluationRepository.register(
                EvaluationRepositoryId.generate(), projectId, repository, branch, clock.instant(), sonarProjectKey);
        candidate.markProvisioned(clock.instant());
        var registered = registerCandidate(candidate);
        if (!registered.getSonarProjectKey().equals(sonarProjectKey)) {
            throw new IllegalStateException("Repository already uses a different Sonar project key");
        }
        return registered;
    }

    private EvaluationRepository registerCandidate(EvaluationRepository candidate) {
        EvaluationRepository registered = registrationPort.registerIfAbsent(candidate);
        if (!registered.getProjectId().equals(candidate.getProjectId())
                || !registered.getAnalysisBranch().equals(candidate.getAnalysisBranch())) {
            throw new IllegalStateException("GitHub repository is already registered with another project or analysis branch");
        }
        return registered;
    }
}
