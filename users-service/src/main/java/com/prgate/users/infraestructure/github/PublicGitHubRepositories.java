package com.prgate.users.infraestructure.github;
import com.fasterxml.jackson.databind.JsonNode;
import com.prgate.users.domain.model.GitHubAccountLink;
import com.prgate.users.application.github.GitHubLinkException;
import org.springframework.stereotype.Service;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.web.client.*;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.*;
import java.util.function.Function;
import org.springframework.web.server.ResponseStatusException;
import static com.prgate.users.application.github.GitHubLinkException.Reason.*;
/** Historical class name retained; includes authorized private repositories. */
@Service
@ConditionalOnProperty(name="github.enabled", havingValue="true")
public class PublicGitHubRepositories {
    private final RestClient client;
    private final Function<GitHubAccountLink,String> tokens;
    @Autowired
    public PublicGitHubRepositories(RestClient.Builder builder, GitHubAccessTokens tokens) {
        this(builder.clone().baseUrl("https://api.github.com").requestFactory(factory()).build(), tokens::accessToken);
    }
    PublicGitHubRepositories(RestClient client, Function<GitHubAccountLink,String> tokens) { this.client=client; this.tokens=tokens; }
    private static JdkClientHttpRequestFactory factory() {
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build());
        factory.setReadTimeout(Duration.ofSeconds(10)); return factory;
    }
    private void validatePage(int page) {
        if (page < 1 || page > 1000) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid page");
    }
    public OrganizationPage organizations(GitHubAccountLink link, int page) {
        validatePage(page);
        var response = get(tokens.apply(link), "/user/orgs?per_page=50&page={page}", page);
        array(response);
        var items = new ArrayList<Organization>();
        for (var item : response) {
            long id=item.path("id").asLong(); String login=item.path("login").asText();
            if (id <= 0 || !login.matches("[A-Za-z0-9-]{1,100}")) throw new GitHubLinkException(PROVIDER_UNAVAILABLE);
            items.add(new Organization(id, login, description(item)));
        }
        return new OrganizationPage(items,page,response.size()==50);
    }
    public Page list(GitHubAccountLink link, int page) { return list(link,page,null); }
    public Page list(GitHubAccountLink link, int page, String organization) {
        validatePage(page);
        String token=tokens.apply(link);
        boolean org=organization!=null && !organization.isBlank();
        JsonNode response;
        if (org) {
            requireMembership(token,organization);
            response=get(token,"/orgs/{org}/repos?type=all&sort=updated&per_page=50&page={page}",organization,page);
        } else {
            response=get(token,"/user/repos?affiliation=owner&visibility=all&sort=updated&per_page=50&page={page}",page);
        }
        array(response);
        var items=new ArrayList<Repository>();
        for (var item:response) {
            if (org ? "Organization".equals(item.path("owner").path("type").asText()) && organization.equalsIgnoreCase(item.path("owner").path("login").asText())
                    : item.path("owner").path("id").asLong()==link.githubUserId()) items.add(map(item));
        }
        return new Page(items,page,response.size()==50);
    }
    public Repository owned(GitHubAccountLink link,String owner,String name) {
        if (!owner.matches("[A-Za-z0-9-]{1,100}") || !name.matches("[A-Za-z0-9_.-]{1,100}"))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid repository");
        String token=tokens.apply(link);
        var item=get(token,"/repos/{owner}/{name}",owner,name);
        if (item.path("owner").path("id").asLong()!=link.githubUserId()) {
            if (!"Organization".equals(item.path("owner").path("type").asText())) throw new GitHubLinkException(REPOSITORY_ACCESS_DENIED);
            requireMembership(token,item.path("owner").path("login").asText());
        }
        return map(item);
    }
    private void requireMembership(String token,String org) {
        if (!org.matches("[A-Za-z0-9-]{1,100}")) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid organization");
        var membership=get(token,"/user/memberships/orgs/{org}",org);
        if (!"active".equals(membership.path("state").asText())) throw new GitHubLinkException(REPOSITORY_ACCESS_DENIED);
    }
    private JsonNode get(String token,String uri,Object... args) {
        try {
            var result=client.get().uri(uri,args).header(HttpHeaders.AUTHORIZATION,"Bearer "+token)
                .header(HttpHeaders.ACCEPT,"application/vnd.github+json").header("X-GitHub-Api-Version","2026-03-10")
                .retrieve().body(JsonNode.class);
            if (result==null) throw new GitHubLinkException(PROVIDER_UNAVAILABLE);
            return result;
        } catch (RestClientResponseException failure) {
            int status=failure.getStatusCode().value();
            if (status==401) throw new GitHubLinkException(REAUTHORIZATION_REQUIRED);
            if (status==429 || (status==403 && failure.getResponseHeaders()!=null &&
                ("0".equals(failure.getResponseHeaders().getFirst("X-RateLimit-Remaining")) || failure.getResponseHeaders().containsKey("Retry-After"))))
                throw new GitHubLinkException(RATE_LIMITED);
            if (status==403 || status==404) throw new GitHubLinkException(REPOSITORY_ACCESS_DENIED);
            throw new GitHubLinkException(PROVIDER_UNAVAILABLE);
        } catch (RestClientException unavailable) { throw new GitHubLinkException(PROVIDER_UNAVAILABLE); }
    }
    private void array(JsonNode response) { if (!response.isArray()) throw new GitHubLinkException(PROVIDER_UNAVAILABLE); }
    private String description(JsonNode item) { return item.path("description").isTextual()?item.path("description").asText():""; }
    private Repository map(JsonNode item) {
        long id=item.path("id").asLong(); String owner=item.path("owner").path("login").asText(),name=item.path("name").asText();
        if (id<=0 || !owner.matches("[A-Za-z0-9-]{1,100}") || !name.matches("[A-Za-z0-9_.-]{1,100}") || !item.path("private").isBoolean())
            throw new GitHubLinkException(PROVIDER_UNAVAILABLE);
        return new Repository(id,owner,name,owner+"/"+name,"https://github.com/"+owner+"/"+name,description(item),item.path("archived").asBoolean(),item.path("private").asBoolean());
    }
    public record Repository(long id,String owner,String name,String fullName,String url,String description,boolean archived,boolean privateRepository) {}
    public record Page(List<Repository> items,int page,boolean hasNext) {}
    public record Organization(long id,String login,String description) {}
    public record OrganizationPage(List<Organization> items,int page,boolean hasNext) {}
}
