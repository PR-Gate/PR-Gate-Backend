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
