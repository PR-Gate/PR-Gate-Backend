package com.prgate.users.infraestructure.github;
import com.prgate.users.domain.model.GitHubAccountLink;
import com.prgate.users.application.github.GitHubLinkException;
import org.junit.jupiter.api.*;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.http.*;
import java.time.Instant;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
import static com.prgate.users.application.github.GitHubLinkException.Reason.*;
class PublicGitHubRepositoriesTest {
    MockRestServiceServer server; PublicGitHubRepositories repositories;
    GitHubAccountLink link=new GitHubAccountLink(UUID.randomUUID(),42,"ana",Instant.now());
    static final String REPO="{\"id\":3,\"private\":true,\"name\":\"tesis\",\"owner\":{\"id\":90,\"login\":\"university\",\"type\":\"Organization\"}}";
    @BeforeEach void setup(){var builder=RestClient.builder().baseUrl("https://api.github.com");
        server=MockRestServiceServer.bindTo(builder).build();repositories=new PublicGitHubRepositories(builder.build(),ignored->"fixture-token");}
    @Test void listsPrivateOwnedRepositoriesWithTheServerToken(){
        server.expect(requestTo("https://api.github.com/user/repos?affiliation=owner&visibility=all&sort=updated&per_page=50&page=1"))
            .andExpect(header("Authorization","Bearer fixture-token"))
            .andRespond(withSuccess("[{\"id\":1,\"private\":true,\"name\":\"tesis\",\"owner\":{\"id\":42,\"login\":\"ana\"}},"+REPO+"]",MediaType.APPLICATION_JSON));
        var result=repositories.list(link,1);
        assertEquals(1,result.items().size()); assertTrue(result.items().getFirst().privateRepository()); server.verify();
    }
    @Test void rejectsForeignPersonalRepositories(){
        server.expect(requestTo("https://api.github.com/repos/other/project"))
            .andRespond(withSuccess("{\"id\":1,\"private\":false,\"name\":\"project\",\"owner\":{\"id\":99,\"login\":\"other\"}}",MediaType.APPLICATION_JSON));
        assertEquals(REPOSITORY_ACCESS_DENIED,assertThrows(GitHubLinkException.class,()->repositories.owned(link,"other","project")).reason()); server.verify();
    }
    @Test void queriesOrganizationsWithUserAuthorization(){
        server.expect(requestTo("https://api.github.com/user/orgs?per_page=50&page=2"))
            .andExpect(header("Authorization","Bearer fixture-token"))
            .andRespond(withSuccess("[{\"id\":90,\"login\":\"university\",\"description\":null}]",MediaType.APPLICATION_JSON));
        assertEquals("university",repositories.organizations(link,2).items().getFirst().login()); server.verify();
    }
    @Test void includesPrivateOrganizationRepositoriesAfterActiveMembershipCheck(){
        membership("active");
        server.expect(requestTo("https://api.github.com/orgs/university/repos?type=all&sort=updated&per_page=50&page=1"))
            .andExpect(header("Authorization","Bearer fixture-token"))
            .andRespond(withSuccess("["+REPO+"]",MediaType.APPLICATION_JSON));
        assertTrue(repositories.list(link,1,"university").items().getFirst().privateRepository()); server.verify();
    }
    @Test void rechecksPrivateRepositoryAccessAndOrganizationMembershipWhenSelected(){
        server.expect(requestTo("https://api.github.com/repos/university/tesis"))
            .andExpect(header("Authorization","Bearer fixture-token"))
            .andRespond(withSuccess(REPO,MediaType.APPLICATION_JSON));
        membership("active");
        assertEquals("university/tesis",repositories.owned(link,"university","tesis").fullName()); server.verify();
    }
    @Test void rejectsPendingMembershipBeforeListing(){
        membership("pending");
        assertEquals(REPOSITORY_ACCESS_DENIED,assertThrows(GitHubLinkException.class,()->repositories.list(link,1,"university")).reason()); server.verify();
    }
    @Test void mapsRevokedTokenToRenewalWithoutLeakingProviderBody(){
        server.expect(requestTo("https://api.github.com/repos/ana/tesis"))
            .andRespond(withStatus(HttpStatus.UNAUTHORIZED).body("sensitive provider details"));
        var failure=assertThrows(GitHubLinkException.class,()->repositories.owned(link,"ana","tesis"));
        assertEquals(REAUTHORIZATION_REQUIRED,failure.reason()); assertFalse(failure.getMessage().contains("sensitive")); server.verify();
    }
    @Test void inaccessiblePrivateRepoIsRejected(){
        server.expect(requestTo("https://api.github.com/repos/ana/tesis")).andRespond(withResourceNotFound());
        assertEquals(REPOSITORY_ACCESS_DENIED,assertThrows(GitHubLinkException.class,()->repositories.owned(link,"ana","tesis")).reason()); server.verify();
    }
    private void membership(String state){
        server.expect(requestTo("https://api.github.com/user/memberships/orgs/university"))
            .andExpect(header("Authorization","Bearer fixture-token"))
            .andRespond(withSuccess("{\"state\":\""+state+"\"}",MediaType.APPLICATION_JSON));
    }
}
