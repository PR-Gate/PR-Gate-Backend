package com.prgate.cicd.interfaces.http;

import com.prgate.cicd.application.port.in.QueryPipelineRunsUseCase;
import com.prgate.cicd.interfaces.http.PipelineRunController.PipelineRunResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/pipeline-runs/projects")
public class ProjectPipelineRunController {
    private final QueryPipelineRunsUseCase queries;
    private final String projectsUrl;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();

    public ProjectPipelineRunController(QueryPipelineRunsUseCase queries,
            @Value("${PIPELINE_PROJECTS_URL:http://localhost:8083}") String projectsUrl) {
        this.queries = queries;
        this.projectsUrl = projectsUrl.replaceAll("/+$", "");
    }

    @GetMapping("/{projectId}")
    public List<PipelineRunResponse> list(@PathVariable UUID projectId,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String bearer,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        requireProjectAccess(projectId, bearer);
        try {
            return queries.listProject(projectId, page, size).stream().map(PipelineRunResponse::from).toList();
        } catch (IllegalArgumentException failure) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, failure.getMessage());
        }
    }

    private void requireProjectAccess(UUID projectId, String bearer) {
        if (bearer == null || !bearer.startsWith("Bearer ")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        var request = HttpRequest.newBuilder(URI.create(projectsUrl + "/api/v1/projects/" + projectId + "/pipeline-access"))
                .header(HttpHeaders.AUTHORIZATION, bearer).timeout(Duration.ofSeconds(8)).GET().build();
        try {
            int status = http.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
            if (status == 204) return;
            if (status == 401) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
            if (status == 403) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Projects access check interrupted");
        } catch (IOException failure) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Projects access check unavailable");
        }
        throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Projects access check unavailable");
    }
}
