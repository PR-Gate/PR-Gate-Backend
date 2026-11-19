package com.prgate.projects.interfaces.rest.controller;

import com.prgate.projects.application.port.in.ProjectQueries;
import com.prgate.projects.application.query.PageRequest;
import com.prgate.projects.application.result.*;
import com.prgate.projects.application.security.Actor;
import com.prgate.projects.application.service.EvaluationAccessService;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/internal/v1/projects/catalog")
public class InternalEvaluationCatalogController {
    private final ProjectQueries projects;
    private final EvaluationAccessService access;
    public InternalEvaluationCatalogController(ProjectQueries projects,EvaluationAccessService access) {
        this.projects=projects; this.access=access;
    }
    @GetMapping("/courses")
    public PageResult<CreateCourseResult> courses(@RequestParam UUID userId,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="100") int size) {
        return projects.courses(new Actor(userId),new PageRequest(page,size));
    }
    @GetMapping("/courses/{courseId}/projects")
    public PageResult<ProjectDetails> projects(@PathVariable UUID courseId,@RequestParam UUID userId,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="100") int size) {
        var result=projects.projects(courseId,new Actor(userId),new PageRequest(page,size));
        return new PageResult<>(result.items().stream().filter(p->access.check(p.id(),userId).mayRead()).toList(),
                result.page(),result.size(),result.hasNext());
    }
    @GetMapping("/courses/{courseId}/access")
    public EvaluationAccessService.Access courseAccess(@PathVariable UUID courseId, @RequestParam UUID userId) {
        var course = projects.course(courseId, new Actor(userId));
        return new EvaluationAccessService.Access(true, userId.equals(course.teacherId()), null, courseId);
    }
}
