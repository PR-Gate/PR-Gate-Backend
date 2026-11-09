package com.prgate.cicd.infrastructure.persistence.jpa.adapter;

import com.prgate.cicd.application.port.out.EvaluationRepositoryRegistrationPort;
import com.prgate.cicd.domain.model.aggregates.EvaluationRepository;
import com.prgate.cicd.domain.model.enums.RepositoryProvisioningStatus;
import com.prgate.cicd.domain.model.valueobject.AcademicProjectId;
import com.prgate.cicd.domain.model.valueobject.EvaluationRepositoryId;
import com.prgate.cicd.domain.model.valueobject.GitHubRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.sql.Timestamp;
import java.util.UUID;

@Repository
public class EvaluationRepositoryRegistrationAdapter implements EvaluationRepositoryRegistrationPort {
    private final JdbcTemplate jdbc;

    public EvaluationRepositoryRegistrationAdapter(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    @Transactional
    public EvaluationRepository registerIfAbsent(EvaluationRepository repository) {
        jdbc.update("""
                INSERT INTO cicd.evaluation_repositories
                (id, academic_project_id, github_repository_id, repository_owner, repository_name,
                 sonar_project_key, analysis_branch, provisioning_status, registered_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (github_repository_id) DO NOTHING
                """, repository.getId().value(), repository.getProjectId().value(),
                repository.getGithubRepository().id(), repository.getGithubRepository().owner(),
                repository.getGithubRepository().name(), repository.getSonarProjectKey().value(),
                repository.getAnalysisBranch(), repository.getStatus().name(),
                Timestamp.from(repository.getRegisteredAt()), Timestamp.from(repository.getUpdatedAt()));
        return jdbc.queryForObject("""
                SELECT * FROM cicd.evaluation_repositories WHERE github_repository_id = ?
                """, (row, index) -> {
                    EvaluationRepository restored = EvaluationRepository.reconstitute(
                            new EvaluationRepositoryId(row.getObject("id", UUID.class)),
                            new AcademicProjectId(row.getObject("academic_project_id", UUID.class)),
                            new GitHubRepository(row.getLong("github_repository_id"),
                                    row.getString("repository_owner"), row.getString("repository_name")),
                            row.getString("analysis_branch"), row.getTimestamp("registered_at").toInstant(),
                            row.getTimestamp("updated_at").toInstant(),
                            RepositoryProvisioningStatus.valueOf(row.getString("provisioning_status")));
                    if (!restored.getSonarProjectKey().value().equals(row.getString("sonar_project_key"))) {
                        throw new IllegalStateException("Stored Sonar project key does not match repository identity");
                    }
                    return restored;
                }, repository.getGithubRepository().id());
    }
}
