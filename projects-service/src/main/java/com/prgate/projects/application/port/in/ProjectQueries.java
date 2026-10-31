package com.prgate.projects.application.port.in;

import com.prgate.projects.application.query.PageRequest;
import com.prgate.projects.application.result.CourseEnrollmentResult;
import com.prgate.projects.application.result.CreateCourseResult;
import com.prgate.projects.application.result.PageResult;
import com.prgate.projects.application.result.ProjectDetails;
import com.prgate.projects.application.result.ProjectMembershipResult;
import com.prgate.projects.application.security.Actor;
import java.util.UUID;

public interface ProjectQueries {
    PageResult<CreateCourseResult> courses(Actor actor, PageRequest page);
    PageResult<CreateCourseResult> catalog(Actor actor, PageRequest page);
    CreateCourseResult course(UUID id, Actor actor);
    PageResult<ProjectDetails> projects(UUID courseId, Actor actor, PageRequest page);
    ProjectDetails project(UUID id, Actor actor);
    PageResult<CourseEnrollmentResult> enrollments(UUID courseId, Actor actor, PageRequest page);
    PageResult<CourseEnrollmentResult> ownEnrollments(Actor actor, PageRequest page);
    PageResult<CourseEnrollmentResult> students(UUID courseId, Actor actor, PageRequest page);
    PageResult<ProjectMembershipResult> members(UUID projectId, Actor actor, PageRequest page);
}
