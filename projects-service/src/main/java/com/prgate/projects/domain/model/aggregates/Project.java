package com.prgate.projects.domain.model.aggregates;

import com.prgate.projects.domain.model.valueobject.CourseId;
import com.prgate.projects.domain.model.valueobject.GitHubOrganization;
import com.prgate.projects.domain.model.valueobject.ProjectId;
import com.prgate.projects.domain.model.valueobject.StudentId;

import java.time.Instant;
import java.util.Optional;

/**
 * Aggregate root representing a group's academic project within a course.
 * The responsible teacher is obtained through the course, not duplicated here.
 */
public final class Project {

    private static final int MAX_NAME_LENGTH = 150;
    private static final int MAX_DESCRIPTION_LENGTH = 2000;

    private final ProjectId id;
    private final String name;
    private final String description;
    private final CourseId courseId;
    private final Instant createdAt;
    private GitHubOrganization gitHubOrganization;
    private StudentId leaderId;

    private Project(ProjectId id, String name, String description, CourseId courseId, Instant createdAt) {
        this.id = requireValue(id, "Project id is required");
        this.name = validateName(name);
        this.description = validateDescription(description);
        this.courseId = requireValue(courseId, "Course id is required");
        this.createdAt = requireValue(createdAt, "Creation time is required");
    }

    /**
     * Identity and time are supplied by the caller to keep creation deterministic.
     */
    public static Project create(ProjectId id, String name, String description, CourseId courseId, Instant createdAt) {
        return new Project(id, name, description, courseId, createdAt);
    }

    /** Rebuilds stored state without replaying commands or requiring the former leader. */
    public static Project reconstitute(ProjectId id, String name, String description, CourseId courseId,
                                       Instant createdAt, StudentId leaderId, GitHubOrganization organization) {
        Project project = new Project(id, name, description, courseId, createdAt);
        project.leaderId = leaderId;
        project.gitHubOrganization = organization;
        return project;
    }

    public void designateLeader(StudentId studentId) {
        this.leaderId = requireValue(studentId, "Leader id is required");
    }

    public void removeLeader() {
        this.leaderId = null;
    }

    public Optional<StudentId> getLeaderId() {
        return Optional.ofNullable(leaderId);
    }

    public void linkGitHubOrganization(GitHubOrganization organization, StudentId actorId) {
        requireValue(organization, "GitHub organization is required");
        requireValue(actorId, "Actor id is required");
        if (leaderId == null || !leaderId.equals(actorId)) {
            throw new IllegalStateException("Only the project leader can link a GitHub organization");
        }
        if (gitHubOrganization != null) {
            throw new IllegalStateException("Project already has a linked GitHub organization");
        }
        this.gitHubOrganization = organization;
    }

    public Optional<GitHubOrganization> getGitHubOrganization() {
        return Optional.ofNullable(gitHubOrganization);
    }

    public ProjectId getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public CourseId getCourseId() {
        return courseId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    private static String validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Project name is required");
        }
        String normalized = name.strip();
        if (normalized.length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException("Project name must not exceed 150 characters");
        }
        return normalized;
    }

    private static String validateDescription(String description) {
        String normalized = description == null ? "" : description.strip();
        if (normalized.length() > MAX_DESCRIPTION_LENGTH) {
            throw new IllegalArgumentException("Project description must not exceed 2000 characters");
        }
        return normalized;
    }

    private static <T> T requireValue(T value, String message) {
        if (value == null) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }
}
