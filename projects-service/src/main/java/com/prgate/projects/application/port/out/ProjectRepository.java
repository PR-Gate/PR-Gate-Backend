package com.prgate.projects.application.port.out;

import com.prgate.projects.domain.model.aggregates.Project;
import com.prgate.projects.domain.model.valueobject.ProjectId;

import java.util.Optional;

public interface ProjectRepository {

    /** Completes only after persistence succeeds; failures must propagate to the caller. */
    void save(Project project);

    /** Empty means project not found; persistence failures must propagate. */
    Optional<Project> findById(ProjectId projectId);
}
