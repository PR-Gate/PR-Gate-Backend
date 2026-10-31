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
        if (token == null || token.isBlank()) { throw new IllegalStateException("SONAR_TOKEN is not configured"); }
        String key = repository.getSonarProjectKey().value();
        if (exists(key)) { return; }
        String body = "project=" + encode(key) + "&name=" + encode(repository.getGithubRepository().fullName())
                + "&visibility=private&mainBranch=" + encode(repository.getAnalysisBranch());
        var response = send(request("/api/projects/create")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build());
        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            if (!exists(key)) { throw new IllegalStateException("Sonar project creation could not be confirmed"); }
            return;
        }
        // A concurrent request may have created the same key. Do not accept arbitrary errors as success.
        if ((response.statusCode() == 400 || response.statusCode() == 409) && exists(key)) { return; }
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
