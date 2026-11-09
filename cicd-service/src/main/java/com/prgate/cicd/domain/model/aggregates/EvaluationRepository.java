package com.prgate.cicd.domain.model.aggregates;

import com.prgate.cicd.domain.model.enums.RepositoryProvisioningStatus;
import com.prgate.cicd.domain.model.valueobject.AcademicProjectId;
import com.prgate.cicd.domain.model.valueobject.EvaluationRepositoryId;
import com.prgate.cicd.domain.model.valueobject.GitHubRepository;
import com.prgate.cicd.domain.model.valueobject.SonarProjectKey;
import java.time.Instant;
import java.util.Objects;

/** Repository enrollment; READY confirms provisioning, not a passing quality gate. */
public final class EvaluationRepository {
    private final EvaluationRepositoryId id;
    private final AcademicProjectId projectId;
    private final GitHubRepository githubRepository;
    private final SonarProjectKey sonarProjectKey;
    private final String analysisBranch;
    private final Instant registeredAt;
    private Instant updatedAt;
    private RepositoryProvisioningStatus status;

    private EvaluationRepository(EvaluationRepositoryId id, AcademicProjectId projectId,
                                  GitHubRepository githubRepository, String analysisBranch, Instant registeredAt) {
        this.id = Objects.requireNonNull(id, "Repository id is required");
        this.projectId = Objects.requireNonNull(projectId, "Academic project is required");
        this.githubRepository = Objects.requireNonNull(githubRepository, "GitHub repository is required");
        this.sonarProjectKey = SonarProjectKey.forRepository(id);
        Objects.requireNonNull(analysisBranch, "Analysis branch is required");
        if (analysisBranch.isBlank() || analysisBranch.length() > 255
                || !analysisBranch.equals(analysisBranch.strip())) {
            throw new IllegalArgumentException("Analysis branch must contain between 1 and 255 characters without surrounding whitespace");
        }
        this.analysisBranch = analysisBranch;
        this.registeredAt = Objects.requireNonNull(registeredAt, "Registration time is required");
        this.updatedAt = registeredAt;
        this.status = RepositoryProvisioningStatus.PENDING;
    }

    public static EvaluationRepository register(EvaluationRepositoryId id, AcademicProjectId projectId,
                                                GitHubRepository repository, String analysisBranch, Instant now) {
        return new EvaluationRepository(id, projectId, repository, analysisBranch, now);
    }

    public void markProvisioned(Instant now) { transitionTo(RepositoryProvisioningStatus.READY, now); }

    public static EvaluationRepository reconstitute(EvaluationRepositoryId id, AcademicProjectId projectId,
            GitHubRepository repository, String branch, Instant registeredAt, Instant updatedAt,
            RepositoryProvisioningStatus status) {
        EvaluationRepository result = register(id, projectId, repository, branch, registeredAt);
        Objects.requireNonNull(updatedAt, "Update time is required");
        if (updatedAt.isBefore(registeredAt)) {
            throw new IllegalArgumentException("Update time cannot precede registration");
        }
        result.updatedAt = updatedAt;
        result.status = Objects.requireNonNull(status, "Provisioning status is required");
        return result;
    }
    public void markProvisioningFailed(Instant now) { transitionTo(RepositoryProvisioningStatus.FAILED, now); }

    public void retryProvisioning(Instant now) {
        if (status != RepositoryProvisioningStatus.FAILED) {
            throw new IllegalStateException("Only failed provisioning can be retried");
        }
        changeStatus(RepositoryProvisioningStatus.PENDING, now);
    }

    private void transitionTo(RepositoryProvisioningStatus target, Instant now) {
        if (status == target) { return; }
        if (status != RepositoryProvisioningStatus.PENDING) {
            throw new IllegalStateException("Repository is not awaiting provisioning");
        }
        changeStatus(target, now);
    }

    private void changeStatus(RepositoryProvisioningStatus target, Instant now) {
        Objects.requireNonNull(now, "Update time is required");
        if (now.isBefore(updatedAt)) { throw new IllegalArgumentException("Update time cannot move backwards"); }
        status = target;
        updatedAt = now;
    }

    public EvaluationRepositoryId getId() { return id; }
    public AcademicProjectId getProjectId() { return projectId; }
    public GitHubRepository getGithubRepository() { return githubRepository; }
    public SonarProjectKey getSonarProjectKey() { return sonarProjectKey; }
    public String getAnalysisBranch() { return analysisBranch; }
    public Instant getRegisteredAt() { return registeredAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public RepositoryProvisioningStatus getStatus() { return status; }
}
