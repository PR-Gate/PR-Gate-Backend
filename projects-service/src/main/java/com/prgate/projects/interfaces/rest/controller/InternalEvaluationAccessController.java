package com.prgate.projects.interfaces.rest.controller;

import com.prgate.projects.application.service.EvaluationAccessService;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/internal/v1/projects")
public class InternalEvaluationAccessController {
    private final EvaluationAccessService access;
    public InternalEvaluationAccessController(EvaluationAccessService access) { this.access = access; }

    @GetMapping("/{projectId}/evaluation-access")
    public EvaluationAccessService.Access check(@PathVariable UUID projectId, @RequestParam UUID userId) {
        return access.check(projectId, userId);
    }
}
