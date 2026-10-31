package com.prgate.projects.interfaces.rest.controller;

import com.prgate.projects.application.command.DesignateProjectLeaderCommand;
import com.prgate.projects.application.command.RemoveProjectLeaderCommand;
import com.prgate.projects.application.command.LinkGitHubOrganizationCommand;
import com.prgate.projects.application.command.JoinProjectCommand;
import com.prgate.projects.application.command.TransferProjectMemberCommand;
import com.prgate.projects.application.port.in.DesignateProjectLeaderUseCase;
import com.prgate.projects.application.port.in.RemoveProjectLeaderUseCase;
import com.prgate.projects.application.port.in.LinkGitHubOrganizationUseCase;
import com.prgate.projects.application.port.in.JoinProjectUseCase;
import com.prgate.projects.application.port.in.TransferProjectMemberUseCase;
import com.prgate.projects.application.port.in.ProjectQueries;
import com.prgate.projects.application.query.PageRequest;
import com.prgate.projects.application.result.LinkedOrganizationResult;
import com.prgate.projects.application.result.ProjectLeadershipResult;
import com.prgate.projects.application.result.ProjectMembershipResult;
import com.prgate.projects.application.result.ProjectDetails;
import com.prgate.projects.application.result.PageResult;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/projects")
@Tag(name="Projects", description="Grupos, integrantes, liderazgo y organizaci?n GitHub")
public class ProjectController {
    private final JoinProjectUseCase join;
    private final DesignateProjectLeaderUseCase designate;
    private final RemoveProjectLeaderUseCase remove;
    private final LinkGitHubOrganizationUseCase link;
    private final TransferProjectMemberUseCase transfer;
    private final ProjectQueries queries;
    private final ActorResolver actors;
    public ProjectController(JoinProjectUseCase join, DesignateProjectLeaderUseCase designate,
                             RemoveProjectLeaderUseCase remove, LinkGitHubOrganizationUseCase link,
                             TransferProjectMemberUseCase transfer, ProjectQueries queries, ActorResolver actors) {
        this.join=join; this.designate=designate; this.remove=remove; this.link=link;
        this.transfer=transfer; this.queries=queries; this.actors=actors;
    }
    @GetMapping("/{projectId}")
    public ProjectDetails detail(@PathVariable UUID projectId, Authentication auth) { return queries.project(projectId,actors.resolve(auth)); }
    @GetMapping("/{projectId}/members")
    public PageResult<ProjectMembershipResult> members(@PathVariable UUID projectId, Authentication auth,
                                                      @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="20") int size) {
        return queries.members(projectId,actors.resolve(auth),new PageRequest(page,size));
    }
    @PostMapping("/{projectId}/memberships")
    public ResponseEntity<ProjectMembershipResult> join(@PathVariable UUID projectId, Authentication auth) {
        return ResponseEntity.status(201).body(join.handle(new JoinProjectCommand(projectId),actors.resolve(auth)));
    }
    @PutMapping("/{projectId}/leader")
    public ProjectLeadershipResult designate(@PathVariable UUID projectId, @Valid @RequestBody ApiRequests.DesignateLeader body, Authentication auth) {
        return designate.handle(new DesignateProjectLeaderCommand(projectId,body.studentId()),actors.resolve(auth));
    }
    @DeleteMapping("/{projectId}/leader")
    public ResponseEntity<Void> remove(@PathVariable UUID projectId, Authentication auth) {
        remove.handle(new RemoveProjectLeaderCommand(projectId),actors.resolve(auth));
        return ResponseEntity.noContent().build();
    }
    @PostMapping("/{projectId}/organization")
    public ResponseEntity<LinkedOrganizationResult> link(@PathVariable UUID projectId, @Valid @RequestBody ApiRequests.LinkOrganization body, Authentication auth) {
        return ResponseEntity.status(201).body(link.handle(new LinkGitHubOrganizationCommand(projectId,body.organizationLogin()),actors.resolve(auth)));
    }
    @PostMapping("/{projectId}/transfers")
    public ResponseEntity<ProjectMembershipResult> transfer(@PathVariable UUID projectId, @Valid @RequestBody ApiRequests.TransferMember body, Authentication auth) {
        return ResponseEntity.status(201).body(transfer.handle(new TransferProjectMemberCommand(projectId,body.targetProjectId(),body.studentId()),actors.resolve(auth)));
    }
}
