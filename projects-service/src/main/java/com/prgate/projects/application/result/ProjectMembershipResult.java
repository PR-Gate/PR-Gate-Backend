package com.prgate.projects.application.result;

import com.prgate.projects.domain.model.aggregates.ProjectMembership;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public record ProjectMembershipResult(UUID id, UUID courseId, UUID projectId, UUID studentId,
                                      Instant joinedAt, Optional<Instant> leftAt) {

    public static ProjectMembershipResult from(ProjectMembership membership) {
        return new ProjectMembershipResult(membership.getId().value(), membership.getCourseId().value(),
                membership.getProjectId().value(), membership.getStudentId().value(), membership.getJoinedAt(),
                membership.getLeftAt());
    }
}
