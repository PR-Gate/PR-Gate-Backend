package com.prgate.users.infraestructure.github;
import com.prgate.users.application.github.*;
import com.prgate.users.domain.model.GitHubAccountLink;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.*;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
class GitHubAuthorizationRenewalTest {
    JdbcTemplate jdbc=mock(JdbcTemplate.class);
    UUID user=UUID.randomUUID();
    Instant now=Instant.now();
    PendingGitHubAuthorization pending=new PendingGitHubAuthorization("state",user,"verifier","browser",now.plusSeconds(60));
    GitHubTokenCipher cipher=new GitHubTokenCipher(Base64.getEncoder().encodeToString(new byte[32]));
    JdbcGitHubLinkStore store;
    @BeforeEach @SuppressWarnings("unchecked") void setup(){
        var manager=mock(PlatformTransactionManager.class);
        when(manager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        store=new JdbcGitHubLinkStore(jdbc,new TransactionTemplate(manager),cipher);
        when(jdbc.queryForList(anyString(),eq(String.class),eq(user))).thenReturn(List.of("ACTIVE"));
        when(jdbc.queryForObject(anyString(),eq(Integer.class),any(Object[].class))).thenReturn(1);
        when(jdbc.query(anyString(),any(RowMapper.class),eq(user))).thenReturn(List.of(new GitHubAccountLink(user,42,"ana",now)));
    }
    @Test void renewsSameAccountWithEncryptedToken(){
        var result=store.finish(pending,new GitHubIdentityProvider.Identity(42,"ana","fixture-token",now.plusSeconds(3600)),now);
        assertEquals(42,result.githubUserId());
        verify(jdbc).update(startsWith("UPDATE users.github_account_links"),eq("ana"),any(),argThat((String value)->
            !value.contains("fixture-token") && cipher.decrypt(value,user+":42").equals("fixture-token")),any(),eq(user),eq(42L));
    }
    @Test void renewalCannotReplaceAnExistingIdentity(){
        assertEquals(GitHubLinkException.Reason.ALREADY_LINKED,assertThrows(GitHubLinkException.class,()->
            store.finish(pending,new GitHubIdentityProvider.Identity(99,"other","fixture-token",now.plusSeconds(3600)),now)).reason());
        verify(jdbc,never()).update(anyString(),any(Object[].class));
    }
}
