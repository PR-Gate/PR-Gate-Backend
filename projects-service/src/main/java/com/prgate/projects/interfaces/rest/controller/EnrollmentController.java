package com.prgate.projects.interfaces.rest.controller;

import com.prgate.projects.application.command.ResolveCourseEnrollmentCommand;
import com.prgate.projects.application.port.in.ResolveCourseEnrollmentUseCase;
import com.prgate.projects.application.port.in.ProjectQueries;
import com.prgate.projects.application.query.PageRequest;
import com.prgate.projects.application.result.CourseEnrollmentResult;
import com.prgate.projects.application.result.PageResult;
import com.prgate.projects.interfaces.rest.dto.ApiRequests;
import com.prgate.projects.interfaces.rest.security.ActorResolver;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@Tag(name="Enrollments", description="Solicitudes e historial de admisi?n")
public class EnrollmentController {
    private final ResolveCourseEnrollmentUseCase resolve;
    private final ProjectQueries queries;
    private final ActorResolver actors;
    public EnrollmentController(ResolveCourseEnrollmentUseCase resolve, ProjectQueries queries, ActorResolver actors) {
        this.resolve=resolve; this.queries=queries; this.actors=actors;
    }
    @PatchMapping("/enrollments/{enrollmentId}")
    public CourseEnrollmentResult decide(@PathVariable UUID enrollmentId, @Valid @RequestBody ApiRequests.DecideEnrollment body, Authentication auth) {
        return resolve.handle(new ResolveCourseEnrollmentCommand(enrollmentId,body.decision()),actors.resolve(auth));
    }
    @GetMapping("/me/enrollments")
    public PageResult<CourseEnrollmentResult> own(Authentication auth, @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="20") int size) {
        return queries.ownEnrollments(actors.resolve(auth),new PageRequest(page,size));
    }
}
