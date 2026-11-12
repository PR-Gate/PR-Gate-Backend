package com.prgate.users.infraestructure.github;
import com.prgate.users.application.github.*;
import org.springframework.http.*;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import static com.prgate.users.application.github.GitHubLinkException.Reason.*;
public final class HttpGitHubIdentityProvider implements GitHubIdentityProvider {
    private final RestClient oauth;
    private final RestClient api;
    private final String clientId, clientSecret, redirectUri;
    public HttpGitHubIdentityProvider(RestClient.Builder builder, String clientId, String clientSecret, String redirectUri) {
        this(builder, clientId, clientSecret, redirectUri, "https://github.com", "https://api.github.com");
    }
    // Explicit endpoint injection is for local HTTP contract tests only.
    public HttpGitHubIdentityProvider(RestClient.Builder builder, String clientId, String clientSecret,
                                      String redirectUri, String oauthBase, String apiBase) {
        this.clientId = clientId; this.clientSecret = clientSecret; this.redirectUri = redirectUri;
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NEVER).build());
        factory.setReadTimeout(Duration.ofSeconds(20));
        oauth = builder.clone().baseUrl(oauthBase).requestFactory(factory).build();
        api = builder.clone().baseUrl(apiBase).requestFactory(factory).build();
    }
    public URI authorizationUri(String state, String challenge) {
        return UriComponentsBuilder.fromUriString("https://github.com/login/oauth/authorize")
                .queryParam("client_id", clientId).queryParam("redirect_uri", redirectUri)
                .queryParam("state", state).queryParam("code_challenge", challenge)
                .queryParam("code_challenge_method", "S256").queryParam("prompt", "select_account")
                .build().encode().toUri();
    }
    public Identity identify(String code, String verifier) {
        try {
            var form = new LinkedMultiValueMap<String, String>();
            form.add("client_id", clientId); form.add("client_secret", clientSecret);
            form.add("redirect_uri", redirectUri); form.add("code", code); form.add("code_verifier", verifier);
            var token = oauth.post().uri("/login/oauth/access_token").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .accept(MediaType.APPLICATION_JSON).body(form).retrieve().body(JsonNode.class);
            if (token == null || token.has("error") || !token.path("access_token").isTextual()
                    || token.path("access_token").asText().isBlank()
                    || !"bearer".equalsIgnoreCase(token.path("token_type").asText()))
                throw new GitHubLinkException(PROVIDER_UNAVAILABLE);
            var identity = api.get().uri("/user")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token.path("access_token").asText())
                    .header(HttpHeaders.ACCEPT, "application/vnd.github+json")
                    .header("X-GitHub-Api-Version", "2026-03-10")
                    .retrieve().body(JsonNode.class);
            if (identity == null || !identity.path("id").isIntegralNumber()
                    || !identity.path("id").canConvertToLong() || !identity.path("login").isTextual())
                throw new GitHubLinkException(PROVIDER_UNAVAILABLE);
            java.time.Instant expiresAt = token.path("expires_in").canConvertToLong()
                    ? java.time.Instant.now().plusSeconds(token.path("expires_in").asLong()) : null;
            return new Identity(identity.path("id").asLong(), identity.path("login").asText(),
                    token.path("access_token").asText(), expiresAt);
        } catch (GitHubLinkException safe) { throw safe; }
        catch (Exception failure) { throw new GitHubLinkException(PROVIDER_UNAVAILABLE); }
    }
}
