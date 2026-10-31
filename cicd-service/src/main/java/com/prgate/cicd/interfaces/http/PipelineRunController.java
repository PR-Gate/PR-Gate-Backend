package com.prgate.cicd.interfaces.http;

import com.prgate.cicd.application.port.in.AuthorizedPipelineQueriesUseCase;
import com.prgate.cicd.domain.model.aggregates.PipelineRun;
import com.prgate.cicd.domain.model.valueobject.SonarAnalysisResult;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.security.Principal;

@RestController
@RequestMapping("/api/v1/pipeline-runs")
public class PipelineRunController {
    private final AuthorizedPipelineQueriesUseCase query;

    public PipelineRunController(AuthorizedPipelineQueriesUseCase query) { this.query = query; }

    @GetMapping("/{id}")
    public PipelineRunResponse get(@PathVariable UUID id, Principal principal) {
        return query.findById(requireUserId(principal), id).map(PipelineRunResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pipeline run not found"));
    }

    @GetMapping
    public List<PipelineRunResponse> list(@RequestParam long repositoryId,
                                         @RequestParam(required = false) String branch,
                                         @RequestParam(defaultValue = "0") int page,
                                         @RequestParam(defaultValue = "20") int size, Principal principal) {
        UUID userId = requireUserId(principal);
        try {
            return query.list(userId, repositoryId, branch, page, size).stream().map(PipelineRunResponse::from).toList();
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage());
        }
    }

    private UUID requireUserId(Principal principal) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Verified authentication is required");
        }
        try {
            UUID id = UUID.fromString(principal.getName());
            if (!id.toString().equalsIgnoreCase(principal.getName())) { throw new IllegalArgumentException(); }
            return id;
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Identity has not been resolved to a PRGate user");
        }
    }

    public record PipelineRunResponse(UUID id, UUID academicProjectId, UUID evaluationRepositoryId,
                                      long repositoryId, String repository,
                                      long githubRunId, int attempt, String workflowName,
                                      String branch, String commitSha, String trigger, String status,
                                      String conclusion, String runUrl, Instant createdAt,
                                      Instant startedAt, Instant completedAt, SonarAnalysisResult sonarAnalysis) {
        static PipelineRunResponse from(PipelineRun run) {
            var reference = run.getProjectReference();
            return new PipelineRunResponse(run.getId().value(),
                    reference.map(value -> value.projectId().value()).orElse(null),
                    reference.map(value -> value.evaluationRepositoryId().value()).orElse(null), run.getRepository().id(),
                    run.getRepository().fullName(), run.getGithubRunId().value(),
                    run.getGithubRunAttempt().value(), run.getWorkflowName(), run.getBranch(),
                    run.getCommitSha().value(), run.getTrigger().name(), run.getStatus().name(),
                    run.getConclusion().map(Enum::name).orElse(null), run.getRunUrl().toString(),
                    run.getCreatedAt(), run.getStartedAt().orElse(null), run.getCompletedAt().orElse(null),
                    run.getSonarAnalysis().orElse(null));
        }
    }
}
