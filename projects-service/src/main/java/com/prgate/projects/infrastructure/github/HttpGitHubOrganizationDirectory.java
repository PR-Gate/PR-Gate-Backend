package com.prgate.projects.infrastructure.github;

import com.prgate.projects.application.exception.GitHubDirectoryException;
import com.prgate.projects.application.port.out.GitHubOrganizationDirectory;
import com.prgate.projects.domain.model.valueobject.GitHubOrganization;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Optional;

@Component
public class HttpGitHubOrganizationDirectory implements GitHubOrganizationDirectory {
    private final RestClient client;

    @Autowired
    public HttpGitHubOrganizationDirectory(RestClient.Builder builder) {
        var http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER).build();
        var factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(Duration.ofSeconds(10));
        this.client = builder.clone().baseUrl("https://api.github.com").requestFactory(factory)
                .defaultHeader("Accept", "application/vnd.github+json")
                .defaultHeader("X-GitHub-Api-Version", "2022-11-28")
                .defaultHeader("User-Agent", "PRGate-Projects-Service").build();
    }

    HttpGitHubOrganizationDirectory(RestClient client) {
        this.client = client;
    }

    @Override
    public Optional<GitHubOrganization> findByLogin(String login) {
        String normalized = GitHubOrganization.normalizeLogin(login);
        try {
            var response = client.get().uri("/orgs/{login}", normalized).retrieve().body(OrganizationResponse.class);
            if (response == null || response.id() == null || !"Organization".equals(response.type())) {
                throw new GitHubDirectoryException("GitHub returned invalid organization metadata");
            }
            return Optional.of(new GitHubOrganization(response.id(), response.login()));
        } catch (RestClientResponseException failure) {
            if (failure.getStatusCode().value() == 404) {
                return Optional.empty();
            }
            throw new GitHubDirectoryException("GitHub organization lookup failed with status " + failure.getStatusCode().value());
        } catch (RestClientException | IllegalArgumentException failure) {
            throw new GitHubDirectoryException("GitHub organization lookup failed", failure);
        }
    }

    private record OrganizationResponse(Long id, String login, String type) { }
}
