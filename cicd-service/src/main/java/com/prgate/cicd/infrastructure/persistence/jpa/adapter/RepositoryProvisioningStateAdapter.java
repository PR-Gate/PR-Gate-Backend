package com.prgate.cicd.infrastructure.persistence.jpa.adapter;

import com.prgate.cicd.application.port.out.RepositoryProvisioningStatePort;
import com.prgate.cicd.domain.model.aggregates.EvaluationRepository;
import com.prgate.cicd.domain.model.enums.RepositoryProvisioningStatus;
import com.prgate.cicd.domain.model.valueobject.AcademicProjectId;
import com.prgate.cicd.domain.model.valueobject.EvaluationRepositoryId;
import com.prgate.cicd.domain.model.valueobject.GitHubRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public class RepositoryProvisioningStateAdapter implements RepositoryProvisioningStatePort,
        com.prgate.cicd.application.port.out.EvaluationRepositoryLookupPort {
    private final JdbcTemplate jdbc;
    public RepositoryProvisioningStateAdapter(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    @Transactional(readOnly = true)
    public Optional<EvaluationRepository> findByGithubRepositoryId(long githubRepositoryId) {
        var ids = jdbc.query("SELECT id FROM cicd.evaluation_repositories WHERE github_repository_id = ?",
                (row, index) -> new EvaluationRepositoryId(row.getObject("id", UUID.class)), githubRepositoryId);
        return ids.stream().findFirst().flatMap(this::findById);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<EvaluationRepository> findById(EvaluationRepositoryId id) {
        return jdbc.query("SELECT * FROM cicd.evaluation_repositories WHERE id = ?", (row, index) ->
                EvaluationRepository.reconstitute(
                        new EvaluationRepositoryId(row.getObject("id", UUID.class)),
                        new AcademicProjectId(row.getObject("academic_project_id", UUID.class)),
                        new GitHubRepository(row.getLong("github_repository_id"), row.getString("repository_owner"),
                                row.getString("repository_name")), row.getString("analysis_branch"),
                        row.getTimestamp("registered_at").toInstant(), row.getTimestamp("updated_at").toInstant(),
                        RepositoryProvisioningStatus.valueOf(row.getString("provisioning_status"))), id.value())
                .stream().findFirst();
    }

    @Override
    @Transactional
    public boolean changeStatus(EvaluationRepositoryId id, RepositoryProvisioningStatus expected,
                                RepositoryProvisioningStatus target, Instant now) {
        return jdbc.update("""
                UPDATE cicd.evaluation_repositories
                SET provisioning_status = ?, updated_at = GREATEST(updated_at, ?)
                WHERE id = ? AND provisioning_status = ?
                """, target.name(), Timestamp.from(now), id.value(), expected.name()) == 1;
    }
}
