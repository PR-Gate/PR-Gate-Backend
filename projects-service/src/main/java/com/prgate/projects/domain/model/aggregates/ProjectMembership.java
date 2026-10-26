package com.prgate.projects.domain.model.aggregates;

import com.prgate.projects.domain.model.valueobject.CourseId;
import com.prgate.projects.domain.model.valueobject.ProjectId;
import com.prgate.projects.domain.model.valueobject.ProjectMembershipId;
import com.prgate.projects.domain.model.valueobject.StudentId;

import java.time.Instant;
import java.util.Optional;

/**
 * Aggregate root recording a student's participation in a project over time.
 * Closing a participation preserves its original project and student references.
 */
public final class ProjectMembership {

    private final ProjectMembershipId id;
    private final CourseId courseId;
    private final ProjectId projectId;
    private final StudentId studentId;
    private final Instant joinedAt;
    private Instant leftAt;

    private ProjectMembership(ProjectMembershipId id, CourseId courseId, ProjectId projectId,
                              StudentId studentId, Instant joinedAt) {
        this.id = requireValue(id, "Project membership id is required");
        this.courseId = requireValue(courseId, "Course id is required");
        this.projectId = requireValue(projectId, "Project id is required");
        this.studentId = requireValue(studentId, "Student id is required");
        this.joinedAt = requireValue(joinedAt, "Join time is required");
    }

    public static ProjectMembership join(ProjectMembershipId id, CourseId courseId, ProjectId projectId,
                                         StudentId studentId, Instant joinedAt) {
        return new ProjectMembership(id, courseId, projectId, studentId, joinedAt);
    }

    public static ProjectMembership reconstitute(ProjectMembershipId id, CourseId courseId, ProjectId projectId,
                                                StudentId studentId, Instant joinedAt, Instant leftAt) {
        ProjectMembership membership = new ProjectMembership(id, courseId, projectId, studentId, joinedAt);
        if (leftAt != null) {
            if (leftAt.isBefore(joinedAt)) {
                throw new IllegalArgumentException("Leave time must not precede join time");
            }
            membership.leftAt = leftAt;
        }
        return membership;
    }

    public void close(Instant leftAt) {
        if (!isActive()) {
            throw new IllegalStateException("Only active project memberships can be closed");
        }
        requireValue(leftAt, "Leave time is required");
        if (leftAt.isBefore(joinedAt)) {
            throw new IllegalArgumentException("Leave time must not precede join time");
        }
        this.leftAt = leftAt;
    }

    public boolean isActive() {
        return leftAt == null;
    }

    public ProjectMembershipId getId() {
        return id;
    }

    public CourseId getCourseId() {
        return courseId;
    }

    public ProjectId getProjectId() {
        return projectId;
    }

    public StudentId getStudentId() {
        return studentId;
    }

    public Instant getJoinedAt() {
        return joinedAt;
    }

    public Optional<Instant> getLeftAt() {
        return Optional.ofNullable(leftAt);
    }

    private static <T> T requireValue(T value, String message) {
        if (value == null) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }
}
