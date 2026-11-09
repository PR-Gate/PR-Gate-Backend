package com.prgate.projects.interfaces.rest.controller;

import com.prgate.projects.application.command.CreateCourseCommand;
import com.prgate.projects.application.command.CreateProjectCommand;
import com.prgate.projects.application.command.RequestCourseEnrollmentCommand;
import com.prgate.projects.application.port.in.CreateCourseUseCase;
import com.prgate.projects.application.port.in.CreateProjectUseCase;
import com.prgate.projects.application.port.in.RequestCourseEnrollmentUseCase;
import com.prgate.projects.application.port.in.ProjectQueries;
import com.prgate.projects.application.query.PageRequest;
import com.prgate.projects.application.result.CourseEnrollmentResult;
import com.prgate.projects.application.result.CreateCourseResult;
import com.prgate.projects.application.result.CreateProjectResult;
import com.prgate.projects.application.result.PageResult;
import com.prgate.projects.application.result.ProjectDetails;
import com.prgate.projects.interfaces.rest.dto.ApiRequests;
import com.prgate.projects.interfaces.rest.security.ActorResolver;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/courses")
@Tag(name="Courses", description="Cursos, proyectos y admisiones")
public class CourseController {
    private final CreateCourseUseCase createCourse;
    private final CreateProjectUseCase createProject;
    private final RequestCourseEnrollmentUseCase requestEnrollment;
    private final ProjectQueries queries;
    private final ActorResolver actors;
    public CourseController(CreateCourseUseCase createCourse, CreateProjectUseCase createProject,
                            RequestCourseEnrollmentUseCase requestEnrollment, ProjectQueries queries, ActorResolver actors) {
        this.createCourse=createCourse; this.createProject=createProject;
        this.requestEnrollment=requestEnrollment; this.queries=queries; this.actors=actors;
    }
    @PostMapping
    public ResponseEntity<CreateCourseResult> create(@Valid @RequestBody ApiRequests.CreateCourse body, Authentication auth) {
        var result=createCourse.handle(new CreateCourseCommand(body.name(),body.section(),body.academicPeriod()),actors.resolve(auth));
        return ResponseEntity.created(URI.create("/api/v1/courses/"+result.id())).body(result);
    }
    @GetMapping
    public PageResult<CreateCourseResult> list(Authentication auth, @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="20") int size) {
        return queries.courses(actors.resolve(auth),new PageRequest(page,size));
    }
    @GetMapping("/catalog")
    public PageResult<CreateCourseResult> catalog(Authentication auth, @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="20") int size) {
        return queries.catalog(actors.resolve(auth),new PageRequest(page,size));
    }
    @GetMapping("/{courseId}")
    public CreateCourseResult detail(@PathVariable UUID courseId, Authentication auth) {
        return queries.course(courseId,actors.resolve(auth));
    }
    @PostMapping("/{courseId}/projects")
    public ResponseEntity<CreateProjectResult> createProject(@PathVariable UUID courseId, @Valid @RequestBody ApiRequests.CreateProject body, Authentication auth) {
        var result=createProject.handle(new CreateProjectCommand(courseId,body.name(),body.description()),actors.resolve(auth));
        return ResponseEntity.created(URI.create("/api/v1/projects/"+result.id())).body(result);
    }
    @GetMapping("/{courseId}/projects")
    public PageResult<ProjectDetails> projects(@PathVariable UUID courseId, Authentication auth,
                                              @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="20") int size) {
        return queries.projects(courseId,actors.resolve(auth),new PageRequest(page,size));
    }
    @PostMapping("/{courseId}/enrollments")
    public ResponseEntity<CourseEnrollmentResult> requestEnrollment(@PathVariable UUID courseId, Authentication auth) {
        var result=requestEnrollment.handle(new RequestCourseEnrollmentCommand(courseId),actors.resolve(auth));
        return ResponseEntity.status(201).body(result);
    }
    @GetMapping("/{courseId}/enrollments")
    public PageResult<CourseEnrollmentResult> enrollments(@PathVariable UUID courseId, Authentication auth,
                                                        @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="20") int size) {
        return queries.enrollments(courseId,actors.resolve(auth),new PageRequest(page,size));
    }
    @GetMapping("/{courseId}/students")
    public PageResult<CourseEnrollmentResult> students(@PathVariable UUID courseId, Authentication auth,
                                                      @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="20") int size) {
        return queries.students(courseId,actors.resolve(auth),new PageRequest(page,size));
    }
}
