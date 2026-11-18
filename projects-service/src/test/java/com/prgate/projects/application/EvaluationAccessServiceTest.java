package com.prgate.projects.application;
import com.prgate.projects.application.model.UserAccount;
import com.prgate.projects.application.port.out.*;
import com.prgate.projects.application.service.EvaluationAccessService;
import com.prgate.projects.domain.model.aggregates.*;
import com.prgate.projects.domain.model.valueobject.*;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EvaluationAccessServiceTest {
    @Test void onlyResponsibleTeacherMayManageAndOtherGroupsCannotRead() {
        var projects=mock(ProjectRepository.class); var courses=mock(CourseRepository.class);
        var members=mock(ProjectMembershipRepository.class); var users=mock(UserDirectory.class);
        var teacher=UUID.randomUUID(); var student=UUID.randomUUID(); var projectId=new ProjectId(UUID.randomUUID());
        var courseId=new CourseId(UUID.randomUUID());
        when(projects.findById(projectId)).thenReturn(Optional.of(Project.create(projectId,"Test","Description",courseId,Instant.now())));
        when(courses.findById(courseId)).thenReturn(Optional.of(Course.create(courseId,"Course","A","202620",new TeacherId(teacher),Instant.now())));
        when(users.findById(teacher)).thenReturn(Optional.of(new UserAccount(teacher,UserAccount.Role.TEACHER,UserAccount.Status.ACTIVE)));
        when(users.findById(student)).thenReturn(Optional.of(new UserAccount(student,UserAccount.Role.STUDENT,UserAccount.Status.ACTIVE)));
        var service=new EvaluationAccessService(projects,courses,members,users);
        assertTrue(service.check(projectId.value(),teacher).mayManage());
        when(members.findActive(courseId,new StudentId(student))).thenReturn(Optional.of(ProjectMembership.join(
                new ProjectMembershipId(UUID.randomUUID()),courseId,new ProjectId(UUID.randomUUID()),new StudentId(student),Instant.now())));
        assertFalse(service.check(projectId.value(),student).mayRead());
        when(members.findActive(courseId,new StudentId(student))).thenReturn(Optional.of(ProjectMembership.join(
                new ProjectMembershipId(UUID.randomUUID()),courseId,projectId,new StudentId(student),Instant.now())));
        assertTrue(service.check(projectId.value(),student).mayRead());
        assertFalse(service.check(projectId.value(),student).mayManage());
        when(users.findById(teacher)).thenReturn(Optional.of(new UserAccount(teacher,UserAccount.Role.TEACHER,UserAccount.Status.INACTIVE)));
        assertFalse(service.check(projectId.value(),teacher).mayRead());
    }
}
