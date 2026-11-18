package com.prgate.projects.application.service;

import com.prgate.projects.application.model.UserAccount;
import com.prgate.projects.application.port.out.*;
import com.prgate.projects.domain.model.valueobject.*;
import java.util.UUID;

/** Membership and responsible-teacher checks remain owned by Projects. */
public final class EvaluationAccessService {
    private final ProjectRepository projects;
    private final CourseRepository courses;
    private final ProjectMembershipRepository members;
    private final UserDirectory users;

    public EvaluationAccessService(ProjectRepository projects, CourseRepository courses,
                                   ProjectMembershipRepository members, UserDirectory users) {
        this.projects = projects; this.courses = courses; this.members = members; this.users = users;
    }

    public Access check(UUID projectId, UUID userId) {
        var project = projects.findById(new ProjectId(projectId));
        var user = users.findById(userId);
        if (project.isEmpty() || user.isEmpty() || !user.get().id().equals(userId)
                || user.get().status() != UserAccount.Status.ACTIVE) return new Access(false, false, null);
        var course = courses.findById(project.get().getCourseId());
        if (course.isEmpty()) return new Access(false, false, null);
        boolean teacher = user.get().role() == UserAccount.Role.TEACHER
                && course.get().getTeacherId().value().equals(userId);
        boolean member = user.get().role() == UserAccount.Role.STUDENT
                && members.findActive(project.get().getCourseId(), new StudentId(userId))
                .filter(m -> m.getProjectId().value().equals(projectId)).isPresent();
        return new Access(teacher || member, teacher,
                teacher || member ? project.get().getGitHubOrganization().map(GitHubOrganization::login).orElse(null) : null);
    }

    public record Access(boolean mayRead, boolean mayManage, String organization) { }
}
