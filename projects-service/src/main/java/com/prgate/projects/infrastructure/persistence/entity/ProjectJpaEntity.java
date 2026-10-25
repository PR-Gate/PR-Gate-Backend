package com.prgate.projects.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "projects")
public class ProjectJpaEntity {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "course_id", nullable = false, updatable = false)
    private UUID courseId;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(nullable = false, length = 2000)
    private String description;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "leader_id")
    private UUID leaderId;

    @Column(name = "github_org_id")
    private Long gitHubOrganizationId;

    @Column(name = "github_org_login", columnDefinition = "text")
    private String gitHubOrganizationLogin;

    protected ProjectJpaEntity() {
    }

    public ProjectJpaEntity(UUID id, UUID courseId, String name, String description, Instant createdAt,
                            UUID leaderId, Long gitHubOrganizationId, String gitHubOrganizationLogin) {
        this.id = id;
        this.courseId = courseId;
        this.name = name;
        this.description = description;
        this.createdAt = createdAt;
        this.leaderId = leaderId;
        this.gitHubOrganizationId = gitHubOrganizationId;
        this.gitHubOrganizationLogin = gitHubOrganizationLogin;
    }

    public UUID getId() {
        return id;
    }

    public UUID getCourseId() {
        return courseId;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public UUID getLeaderId() {
        return leaderId;
    }

    public Long getGitHubOrganizationId() {
        return gitHubOrganizationId;
    }

    public String getGitHubOrganizationLogin() {
        return gitHubOrganizationLogin;
    }
}
