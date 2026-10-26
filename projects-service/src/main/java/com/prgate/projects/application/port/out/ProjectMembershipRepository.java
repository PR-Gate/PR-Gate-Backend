package com.prgate.projects.application.port.out;

import com.prgate.projects.domain.model.aggregates.ProjectMembership;
import com.prgate.projects.domain.model.valueobject.CourseId;
import com.prgate.projects.domain.model.valueobject.StudentId;

import java.util.Optional;

public interface ProjectMembershipRepository {

    /** Closed participation history is excluded. */
    Optional<ProjectMembership> findActive(CourseId courseId, StudentId studentId);

    /**
     * Inserts a new active participation, never overwriting history. The adapter
     * must enforce one active membership per student/course atomically and raise
     * ProjectMembershipConflictException on conflicts, even after a prior lookup.
     */
    void add(ProjectMembership membership);

    /** Closes only the matching persisted active record; raises a conflict if stale. */
    void close(ProjectMembership membership);
}
