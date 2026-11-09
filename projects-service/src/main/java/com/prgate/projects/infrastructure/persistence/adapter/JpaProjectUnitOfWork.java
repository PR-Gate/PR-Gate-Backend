package com.prgate.projects.infrastructure.persistence.adapter;

import com.prgate.projects.application.exception.ProjectNotFoundException;
import com.prgate.projects.application.port.out.ProjectRepository;
import com.prgate.projects.application.port.out.ProjectUnitOfWork;
import com.prgate.projects.domain.model.aggregates.Project;
import com.prgate.projects.domain.model.valueobject.ProjectId;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.function.Function;

@Component
public class JpaProjectUnitOfWork implements ProjectUnitOfWork {
    private final JdbcTemplate jdbc;
    private final ProjectRepository projects;
    private final TransactionTemplate transactions;

    public JpaProjectUnitOfWork(JdbcTemplate jdbc, ProjectRepository projects, PlatformTransactionManager manager) {
        this.jdbc = jdbc;
        this.projects = projects;
        this.transactions = new TransactionTemplate(manager);
        this.transactions.setTimeout(15);
    }

    @Override
    public <T> T execute(ProjectId projectId, Function<Project, T> operation) {
        return transactions.execute(status -> {
            // One course lock establishes a consistent order for transfers and leader/org updates.
            var locked = jdbc.queryForList("""
                    SELECT c.id FROM courses c JOIN projects p ON p.course_id = c.id
                    WHERE p.id = ? FOR UPDATE OF c
                    """, projectId.value());
            if (locked.isEmpty()) {
                throw new ProjectNotFoundException("Project does not exist");
            }
            Project project = projects.findById(projectId)
                    .orElseThrow(() -> new ProjectNotFoundException("Project does not exist"));
            return operation.apply(project);
        });
    }
}
