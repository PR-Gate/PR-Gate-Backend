package com.prgate.metrics.infrastructure.outbound.projects;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prgate.metrics.application.port.out.ProjectAccessPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.UUID;

@Component
public class ProjectsAccessHttpAdapter implements ProjectAccessPort {
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private final String baseUrl;
    private final String token;
    private final ObjectMapper json;
    public ProjectsAccessHttpAdapter(ObjectMapper json,
            @Value("${app.projects.base-url:http://localhost:8083}") String baseUrl,
            @Value("${app.projects.token:}") String token) {
        URI uri = URI.create(baseUrl);
        if (uri.getHost() == null || uri.getUserInfo() != null || uri.getQuery() != null
                || uri.getFragment() != null || !java.util.Set.of("http","https").contains(uri.getScheme())) {
            throw new IllegalArgumentException("Invalid Projects URL");
        }
        this.baseUrl = baseUrl.replaceAll("/+$", ""); this.token = token; this.json = json;
    }
    public Access check(UUID projectId, UUID actorId) {
        if (token.isBlank()) throw new IllegalStateException("Projects service credentials are not configured");
        try {
            var request = HttpRequest.newBuilder(URI.create(baseUrl + "/internal/v1/projects/" + projectId
                    + "/evaluation-access?userId=" + actorId)).timeout(Duration.ofSeconds(8))
                    .header("Authorization", "Bearer " + token).GET().build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) throw new IllegalStateException("Projects access check unavailable");
            var value = json.readTree(response.body());
            if (!value.path("mayRead").isBoolean() || !value.path("mayManage").isBoolean()) {
                throw new IllegalStateException("Invalid Projects access response");
            }
            return new Access(value.path("mayRead").asBoolean(), value.path("mayManage").asBoolean(),
                    value.path("organization").isTextual() ? value.path("organization").asText() : null);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt(); throw new IllegalStateException("Projects access check interrupted");
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("Projects access check unavailable");
        }
    }
}
