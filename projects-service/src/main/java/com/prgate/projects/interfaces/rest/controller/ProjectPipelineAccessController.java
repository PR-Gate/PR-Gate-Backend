package com.prgate.projects.interfaces.rest.controller;

import com.prgate.projects.application.exception.AccessDeniedException;
import com.prgate.projects.application.service.EvaluationAccessService;
import com.prgate.projects.interfaces.rest.security.ActorResolver;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/projects")
public class ProjectPipelineAccessController {
    private final EvaluationAccessService access;
    private final ActorResolver actors;

    public ProjectPipelineAccessController(EvaluationAccessService access, ActorResolver actors) {
        this.access = access;
        this.actors = actors;
    }

    @GetMapping("/{projectId}/pipeline-access")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void check(@PathVariable UUID projectId, Authentication authentication) {
        if (!access.check(projectId, actors.resolve(authentication).userId()).mayRead()) {
            throw new AccessDeniedException("Project pipeline access denied");
        }
    }
}
