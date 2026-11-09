package com.prgate.projects.infrastructure.config;

import com.prgate.projects.application.port.out.CourseRepository;
import com.prgate.projects.application.port.out.ProjectRepository;
import com.prgate.projects.application.port.out.CourseEnrollmentRepository;
import com.prgate.projects.application.port.out.ProjectMembershipRepository;
import com.prgate.projects.application.port.out.UserDirectory;
import com.prgate.projects.application.port.out.ProjectUnitOfWork;
import com.prgate.projects.application.port.out.GitHubOrganizationDirectory;
import com.prgate.projects.application.port.out.ProjectReadRepository;
import com.prgate.projects.application.service.CreateCourseService;
import com.prgate.projects.application.service.CreateProjectService;
import com.prgate.projects.application.service.RequestCourseEnrollmentService;
import com.prgate.projects.application.service.ResolveCourseEnrollmentService;
import com.prgate.projects.application.service.JoinProjectService;
import com.prgate.projects.application.service.ManageProjectLeaderService;
import com.prgate.projects.application.service.LinkGitHubOrganizationService;
import com.prgate.projects.application.service.TransferProjectMemberService;
import com.prgate.projects.application.service.ProjectQueryService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.time.Clock;

@Configuration
public class ApplicationConfiguration {
    @Bean
    Clock clock() { return Clock.systemUTC(); }

    @Bean
    CreateCourseService createCourseService(CourseRepository courses, UserDirectory users, Clock clock) {
        return new CreateCourseService(courses, users, clock);
    }

    @Bean
    CreateProjectService createProjectService(CourseRepository courses, ProjectRepository projects, UserDirectory users, Clock clock) {
        return new CreateProjectService(courses, projects, users, clock);
    }

    @Bean
    RequestCourseEnrollmentService requestCourseEnrollmentService(CourseRepository courses, CourseEnrollmentRepository enrollments, UserDirectory users, Clock clock) {
        return new RequestCourseEnrollmentService(courses, enrollments, users, clock);
    }

    @Bean
    ResolveCourseEnrollmentService resolveCourseEnrollmentService(CourseRepository courses, CourseEnrollmentRepository enrollments, UserDirectory users, Clock clock) {
        return new ResolveCourseEnrollmentService(courses, enrollments, users, clock);
    }

    @Bean
    JoinProjectService joinProjectService(ProjectRepository projects, CourseEnrollmentRepository enrollments, ProjectMembershipRepository memberships, UserDirectory users, Clock clock) {
        return new JoinProjectService(projects, enrollments, memberships, users, clock);
    }

    @Bean
    ManageProjectLeaderService manageProjectLeaderService(ProjectRepository projects, ProjectMembershipRepository memberships, CourseRepository courses, ProjectUnitOfWork work, UserDirectory users) {
        return new ManageProjectLeaderService(projects, memberships, courses, work, users);
    }

    @Bean
    LinkGitHubOrganizationService linkGitHubOrganizationService(ProjectRepository projects, ProjectMembershipRepository memberships, ProjectUnitOfWork work, GitHubOrganizationDirectory github, UserDirectory users) {
        return new LinkGitHubOrganizationService(projects, memberships, work, github, users);
    }

    @Bean
    TransferProjectMemberService transferProjectMemberService(ProjectRepository projects, ProjectMembershipRepository memberships, CourseEnrollmentRepository enrollments, CourseRepository courses, ProjectUnitOfWork work, UserDirectory users, Clock clock) {
        return new TransferProjectMemberService(projects, memberships, enrollments, courses, work, users, clock);
    }

    @Bean
    ProjectQueryService projectQueryService(CourseRepository courses, ProjectRepository projects, CourseEnrollmentRepository enrollments, ProjectReadRepository reads, UserDirectory users) {
        return new ProjectQueryService(courses, projects, enrollments, reads, users);
    }
}
