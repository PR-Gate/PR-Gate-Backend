package com.prgate.users.interfaces.rest.github;

import com.prgate.users.application.github.GitHubLinkService;
import com.prgate.users.infraestructure.github.PublicGitHubRepositories;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

@RestController
@ConditionalOnProperty(name="github.enabled", havingValue="true")
@RequestMapping("/api/me/integrations/github/repositories")
@SecurityRequirement(name="githubActor")
public class GitHubRepositoriesController {
    @ModelAttribute
    public void privateResponses(jakarta.servlet.http.HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Referrer-Policy", "no-referrer");
    }
    private final GitHubActorResolver actors;
    private final GitHubLinkService links;
    private final PublicGitHubRepositories repositories;
    public GitHubRepositoriesController(GitHubActorResolver actors, GitHubLinkService links, PublicGitHubRepositories repositories) {
        this.actors=actors; this.links=links; this.repositories=repositories;
    }
    @GetMapping
    public PublicGitHubRepositories.Page list(HttpServletRequest request, @RequestParam(defaultValue="1") int page,
            @RequestParam(required=false) String organization) {
        return repositories.list(links.get(actors.resolve(request)), page, organization);
    }
    @GetMapping("/organizations")
    public PublicGitHubRepositories.OrganizationPage organizations(HttpServletRequest request, @RequestParam(defaultValue="1") int page) {
        return repositories.organizations(links.get(actors.resolve(request)), page);
    }
    @GetMapping("/{owner}/{name}")
    public PublicGitHubRepositories.Repository owned(HttpServletRequest request, @PathVariable String owner, @PathVariable String name) {
        return repositories.owned(links.get(actors.resolve(request)), owner, name);
    }
}
