package com.prgate.projects.application.port.out;

import com.prgate.projects.domain.model.aggregates.Project;
import com.prgate.projects.domain.model.valueobject.ProjectId;
import java.util.function.Function;

public interface ProjectUnitOfWork {
    /**
     * Loads the project after serializing management operations in its course.
     * All repository writes in the callback commit together or roll back together.
     * The callback must not perform external network requests.
     */
    <T> T execute(ProjectId projectId, Function<Project, T> operation);
}
