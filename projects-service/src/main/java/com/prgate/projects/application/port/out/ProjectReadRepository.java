package com.prgate.projects.application.port.out;

import com.prgate.projects.application.query.PageRequest;
import com.prgate.projects.application.result.CourseEnrollmentResult;
import com.prgate.projects.application.result.CreateCourseResult;
import com.prgate.projects.application.result.PageResult;
import com.prgate.projects.application.result.ProjectDetails;
import com.prgate.projects.application.result.ProjectMembershipResult;
import com.prgate.projects.application.model.UserAccount;
import java.util.UUID;

public interface ProjectReadRepository {
    PageResult<CreateCourseResult> courses(UserAccount user, PageRequest page);
    PageResult<CreateCourseResult> catalog(PageRequest page);
    PageResult<ProjectDetails> projects(UUID courseId, PageRequest page);
    PageResult<CourseEnrollmentResult> enrollments(UUID courseId, PageRequest page);
    PageResult<CourseEnrollmentResult> ownEnrollments(UUID studentId, PageRequest page);
    PageResult<CourseEnrollmentResult> students(UUID courseId, PageRequest page);
    PageResult<ProjectMembershipResult> members(UUID projectId, PageRequest page);
}
