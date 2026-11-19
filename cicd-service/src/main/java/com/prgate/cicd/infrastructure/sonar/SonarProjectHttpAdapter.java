package com.prgate.cicd.infrastructure.sonar;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prgate.cicd.application.port.out.SonarProjectProvisioningPort;
import com.prgate.cicd.domain.model.aggregates.EvaluationRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.io.IOException;
import java.time.Instant;

@Component
public class SonarProjectHttpAdapter implements SonarProjectProvisioningPort {
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER).build();
    private final ObjectMapper json;
    private final String baseUrl;
    private final String token;

    public SonarProjectHttpAdapter(ObjectMapper json,
            @Value("${app.sonar.base-url:http://localhost:9000}") String baseUrl,
            @Value("${app.sonar.token:}") String token) {
        this.json = json;
        URI uri = URI.create(baseUrl);
        if (uri.getHost() == null || !("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()))
                || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null) {
            throw new IllegalArgumentException("Sonar base URL must be an HTTP(S) URL without credentials, query or fragment");
        }
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.token = token;
    }

    @Override
    public void ensureProjectExists(EvaluationRepository repository) {
        ensureProjectExists(repository.getSonarProjectKey().value(),
                repository.getGithubRepository().fullName(), repository.getAnalysisBranch());
    }

    public void ensurePilotProjectExists(String projectKey) {
        ensureProjectExists(projectKey, "PRGate local file pilot " + projectKey, "main");
    }

    public void requireExistingProject(String projectKey) {
        if (!exists(projectKey)) throw new IllegalStateException("Existing Sonar project was not found");
    }

    private void ensureProjectExists(String key, String name, String branch) {
        if (token == null || token.isBlank()) { throw new IllegalStateException("SONAR_TOKEN is not configured"); }
        if (exists(key)) { return; }
        String body = "project=" + encode(key) + "&name=" + encode(name)
                + "&visibility=private&mainBranch=" + encode(branch);
        var response = send(request("/api/projects/create")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build());
        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            if (!exists(key)) { throw new IllegalStateException("Sonar project creation could not be confirmed"); }
            return;
        }
        // A concurrent request may have created the same key. Do not accept arbitrary errors as success.
        if ((response.statusCode() == 400 || response.statusCode() == 409) && exists(key)) {
            return;
        }
        throw new IllegalStateException("Sonar project creation failed with HTTP " + response.statusCode());
    }

    private boolean exists(String key) {
        var response = send(request("/api/components/show?component=" + encode(key)).GET().build());
        if (response.statusCode() == 404) { return false; }
        if (response.statusCode() != 200) {
            throw new IllegalStateException("Sonar project lookup failed with HTTP " + response.statusCode());
        }
        try {
            return key.equals(json.readTree(response.body()).path("component").path("key").asText());
        } catch (IOException exception) {
            throw new IllegalStateException("Sonar returned an invalid response");
        }
    }


    public String resolveAnalysisId(String taskId, String projectKey) {
        if (token == null || token.isBlank()) { throw new IllegalStateException("SONAR_TOKEN is not configured"); }
        var response = send(request("/api/ce/task?id=" + encode(taskId)).GET().build());
        if (response.statusCode() != 200) { throw new IllegalStateException("Cannot retrieve Sonar task"); }
        try {
            var task = json.readTree(response.body()).path("task");
            if (!"SUCCESS".equals(task.path("status").asText())
                    || !projectKey.equals(task.path("componentKey").asText())
                    || task.path("analysisId").asText().isBlank()) {
                throw new IllegalStateException("Sonar task does not confirm a matching successful analysis");
            }
            return task.path("analysisId").asText();
        } catch (IOException exception) { throw new IllegalStateException("Invalid Sonar task response"); }
    }

    public PilotAnalysis awaitPilotAnalysis(String taskId, String projectKey) {
        Instant deadline = Instant.now().plusSeconds(90);
        while (Instant.now().isBefore(deadline)) {
            var response = send(request("/api/ce/task?id=" + encode(taskId)).GET().build());
            if (response.statusCode() != 200) {
                throw new IllegalStateException("Cannot retrieve Sonar task; HTTP " + response.statusCode());
            }
            try {
                var task = json.readTree(response.body()).path("task");
                if (!projectKey.equals(task.path("componentKey").asText())) {
                    throw new IllegalStateException("Sonar task belongs to a different project");
                }
                String status = task.path("status").asText();
                if ("SUCCESS".equals(status)) {
                    String analysisId = task.path("analysisId").asText();
                    if (analysisId.isBlank()) { throw new IllegalStateException("Sonar task has no analysis ID"); }
                    var gate = send(request("/api/qualitygates/project_status?analysisId=" + encode(analysisId))
                            .GET().build());
                    if (gate.statusCode() != 200) {
                        throw new IllegalStateException("Cannot retrieve Quality Gate; HTTP " + gate.statusCode());
                    }
                    String gateStatus = json.readTree(gate.body()).path("projectStatus").path("status").asText();
                    if (gateStatus.isBlank()) { throw new IllegalStateException("Sonar returned no Quality Gate status"); }
                    return new PilotAnalysis(analysisId, gateStatus);
                }
                if ("FAILED".equals(status) || "CANCELED".equals(status)) {
                    throw new IllegalStateException("Sonar analysis task ended with status " + status);
                }
            } catch (IOException exception) {
                throw new IllegalStateException("Invalid Sonar task or Quality Gate response", exception);
            }
            try { Thread.sleep(2000); }
            catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while waiting for SonarQube", exception);
            }
        }
        throw new IllegalStateException("Sonar analysis did not finish within 90 seconds");
    }

    /** Resolve the exact task, then verify its revision and analysis-specific Quality Gate. */
    public VerifiedAnalysis verifyCompletedAnalysis(String taskId, String projectKey, String revision) {
        String analysisId = resolveAnalysisId(taskId, projectKey);
        try {
            boolean found = false;
            for (int page = 1; page <= 10 && !found; page++) {
                var response = send(request("/api/project_analyses/search?project=" + encode(projectKey)
                        + "&ps=100&p=" + page).GET().build());
                if (response.statusCode() != 200) throw new IllegalStateException("Cannot verify Sonar revision");
                var analyses = json.readTree(response.body()).path("analyses");
                if (!analyses.isArray()) throw new IllegalStateException("Invalid analysis search response");
                for (var analysis : analyses) {
                    if (analysisId.equals(analysis.path("key").asText())) {
                        if (!revision.equals(analysis.path("revision").asText())) {
                            throw new IllegalStateException("Sonar analysis revision mismatch");
                        }
                        found = true;
                        break;
                    }
                }
                if (analyses.size() < 100) break;
            }
            if (!found) throw new IllegalStateException("Exact Sonar analysis is not available");
            var response = send(request("/api/qualitygates/project_status?analysisId=" + encode(analysisId))
                    .GET().build());
            if (response.statusCode() != 200) throw new IllegalStateException("Cannot verify analysis Quality Gate");
            String gate = json.readTree(response.body()).path("projectStatus").path("status").asText();
            if (!java.util.Set.of("OK", "WARN", "ERROR", "NONE").contains(gate)) {
                throw new IllegalStateException("Invalid analysis Quality Gate");
            }
            return new VerifiedAnalysis(analysisId, gate);
        } catch (IOException exception) {
            throw new IllegalStateException("Invalid Sonar verification response");
        }
    }

    public record VerifiedAnalysis(String analysisId, String qualityGateStatus) { }

    /** Each configuration owns a new gate; never changes Sonar way or a shared default. */
    public String configureQualityGate(java.util.UUID configurationId, String projectKey,
            java.util.List<com.prgate.cicd.infrastructure.messaging.QualityGateRequest.Condition> conditions) {
        String name = "prgate-" + configurationId;
        var shown = send(request("/api/qualitygates/show?name=" + encode(name)).GET().build());
        if (shown.statusCode()==404) {
            var created = postForm("/api/qualitygates/create", "name=" + encode(name));
            if (created.statusCode()<200 || created.statusCode()>=300) {
                shown = send(request("/api/qualitygates/show?name=" + encode(name)).GET().build());
                if (shown.statusCode()!=200) throw new IllegalStateException("Cannot create PRGate Quality Gate");
            }
            shown = send(request("/api/qualitygates/show?name=" + encode(name)).GET().build());
        }
        if (shown.statusCode()!=200) throw new IllegalStateException("Cannot inspect PRGate Quality Gate");
        try {
            var gate = json.readTree(shown.body());
            if (!name.equals(gate.path("name").asText()) || gate.path("isBuiltIn").asBoolean(false))
                throw new IllegalStateException("Unexpected Quality Gate identity");
            for (var existing : gate.path("conditions")) {
                var desired = conditions.stream().filter(c -> c.metric().equals(existing.path("metric").asText()))
                        .findFirst().orElse(null);
                if (desired == null) {
                    String metric = existing.path("metric").asText();
                    if (!existing.path("isCaycCondition").asBoolean()
                            || !java.util.Set.of("new_security_hotspots_reviewed", "new_duplicated_lines_density",
                                    "new_coverage", "new_violations").contains(metric)
                            || existing.path("id").asText().isBlank()) {
                        throw new IllegalStateException("Unexpected Quality Gate condition");
                    }
                    var removed = postForm("/api/qualitygates/delete_condition", "id=" + encode(existing.path("id").asText()));
                    if (removed.statusCode()<200 || removed.statusCode()>=300)
                        throw new IllegalStateException("Cannot remove default Quality Gate condition");
                    continue;
                }
                if (!desired.op().equals(existing.path("op").asText())
                        || desired.error().compareTo(new java.math.BigDecimal(existing.path("error").asText()))!=0)
                    throw new IllegalStateException("Quality Gate condition drift");
            }
            for (var condition : conditions) {
                boolean exists=false;
                for (var existing : gate.path("conditions"))
                    if (condition.metric().equals(existing.path("metric").asText())) exists=true;
                if (!exists) {
                    var response = postForm("/api/qualitygates/create_condition", "gateName=" + encode(name)
                            + "&metric=" + encode(condition.metric()) + "&op=" + condition.op()
                            + "&error=" + encode(condition.error().toPlainString()));
                    if (response.statusCode()<200 || response.statusCode()>=300)
                        throw new IllegalStateException("Cannot create Quality Gate condition");
                }
            }
            var verified = send(request("/api/qualitygates/show?name=" + encode(name)).GET().build());
            if (verified.statusCode()!=200) throw new IllegalStateException("Cannot verify Quality Gate");
            var installed = json.readTree(verified.body()).path("conditions");
            if (!installed.isArray() || installed.size()!=conditions.size())
                throw new IllegalStateException("Quality Gate conditions incomplete");
            for (var condition : conditions) {
                boolean match=false;
                for (var existing : installed) if (condition.metric().equals(existing.path("metric").asText())
                        && condition.op().equals(existing.path("op").asText())
                        && condition.error().compareTo(new java.math.BigDecimal(existing.path("error").asText()))==0) match=true;
                if (!match) throw new IllegalStateException("Quality Gate condition mismatch");
            }
            var selected = postForm("/api/qualitygates/select", "gateName=" + encode(name) + "&projectKey=" + encode(projectKey));
            if (selected.statusCode()<200 || selected.statusCode()>=300)
                throw new IllegalStateException("Cannot assign PRGate Quality Gate");
            var projectGate = send(request("/api/qualitygates/get_by_project?project=" + encode(projectKey)).GET().build());
            if (projectGate.statusCode()!=200 || !name.equals(json.readTree(projectGate.body()).path("qualityGate").path("name").asText()))
                throw new IllegalStateException("Quality Gate assignment could not be verified");
            return name;
        } catch (IOException exception) { throw new IllegalStateException("Invalid Sonar Quality Gate response"); }
    }

    private HttpResponse<String> postForm(String path, String body) {
        return send(request(path).header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build());
    }

    public String dashboardUrl(String projectKey) {
        return baseUrl + "/dashboard?id=" + encode(projectKey);
    }

    public record PilotAnalysis(String analysisId, String qualityGateStatus) { }

    private HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder(URI.create(baseUrl + path)).timeout(Duration.ofSeconds(15))
                .header("Authorization", "Bearer " + token).header("Accept", "application/json");
    }

    private HttpResponse<String> send(HttpRequest request) {
        try {
            return client.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Sonar request interrupted");
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot connect to SonarQube");
        }
    }

    private static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
}
