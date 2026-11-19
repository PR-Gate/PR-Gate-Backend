package com.prgate.projects.infrastructure.users;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prgate.projects.application.exception.UserDirectoryException;
import com.prgate.projects.application.model.UserAccount;
import com.prgate.projects.application.port.out.UserDirectory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;

@Component
@Primary
@ConditionalOnProperty(name="prgate.users.remote-enabled",havingValue="true")
public class RemoteUserDirectory implements UserDirectory {
    private final HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private final ObjectMapper json;
    private final String baseUrl,token;
    public RemoteUserDirectory(ObjectMapper json,@Value("${prgate.users.base-url:http://localhost:8080}") String baseUrl,
                                @Value("${prgate.users.service-token:}") String token) {
        URI uri=URI.create(baseUrl);
        if (uri.getHost()==null || uri.getUserInfo()!=null || uri.getQuery()!=null || uri.getFragment()!=null
                || !Set.of("http","https").contains(uri.getScheme())) throw new IllegalArgumentException("Invalid Users URL");
        this.json=json; this.baseUrl=baseUrl.replaceAll("/+$",""); this.token=token;
    }
    public Optional<UserAccount> findById(UUID id) {
        if (token.isBlank()) throw new UserDirectoryException("Users service credentials are not configured");
        try {
            var request=HttpRequest.newBuilder(URI.create(baseUrl+"/internal/v1/users/"+id))
                    .timeout(Duration.ofSeconds(8)).header("Authorization","Bearer "+token).GET().build();
            var response=client.send(request,HttpResponse.BodyHandlers.ofString());
            if (response.statusCode()==404) return Optional.empty();
            if (response.statusCode()!=200) throw new UserDirectoryException("Users lookup unavailable");
            var user=json.readTree(response.body());
            UUID received=UUID.fromString(user.path("id").asText());
            if (!id.equals(received)) throw new UserDirectoryException("Users returned a different identity");
            return Optional.of(new UserAccount(received,UserAccount.Role.valueOf(user.path("role").asText()),
                    UserAccount.Status.valueOf(user.path("status").asText()),
                    (user.path("firstName").asText("") + " " + user.path("lastName").asText("")).strip()));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt(); throw new UserDirectoryException("Users lookup interrupted");
        } catch (java.io.IOException | IllegalArgumentException exception) {
            throw new UserDirectoryException("Invalid Users response");
        }
    }

    public Optional<UUID> authenticate(String bearer) {
        try {
            var request = HttpRequest.newBuilder(URI.create(baseUrl + "/api/me"))
                    .timeout(Duration.ofSeconds(8)).header("Authorization", bearer).GET().build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (Set.of(401, 403, 404).contains(response.statusCode())) return Optional.empty();
            if (response.statusCode() != 200) throw new UserDirectoryException("Users session lookup unavailable");
            var profile = json.readTree(response.body());
            if (!"ACTIVE".equals(profile.path("status").asText())) return Optional.empty();
            return Optional.of(UUID.fromString(profile.path("id").asText()));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new UserDirectoryException("Users session lookup interrupted");
        } catch (java.io.IOException | IllegalArgumentException exception) {
            throw new UserDirectoryException("Invalid Users session response");
        }
    }
}
