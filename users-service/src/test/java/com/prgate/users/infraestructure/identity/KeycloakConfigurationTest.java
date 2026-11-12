package com.prgate.users.infraestructure.identity;

import com.prgate.users.infraestructure.github.GitHubLinkConfiguration;
import com.prgate.users.infraestructure.persistence.jpa.repositories.UserRepository;
import com.prgate.users.interfaces.rest.github.GitHubActorResolver;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.client.RestClient;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class KeycloakConfigurationTest {
    @Test void keycloakWinsOverLocalDevelopmentIdentityWithoutDuplicateActorBeans() {
        new ApplicationContextRunner()
            .withInitializer(context -> context.getEnvironment().setActiveProfiles("local"))
            .withUserConfiguration(GitHubLinkConfiguration.class, KeycloakIdentityConfiguration.class)
            .withPropertyValues("prgate.auth.provider=keycloak",
                "prgate.auth.issuer=http://localhost:8090/realms/prgate",
                "prgate.auth.jwk-set-uri=http://localhost:8090/realms/prgate/protocol/openid-connect/certs",
                "github.enabled=true", "github.client-id=fixture", "github.client-secret=fixture",
                "github.redirect-uri=http://localhost:8080/api/integrations/github/callback")
            .withBean(UserRepository.class, () -> mock(UserRepository.class))
            .withBean(JdbcTemplate.class, () -> mock(JdbcTemplate.class))
            .withBean(PlatformTransactionManager.class, () -> mock(PlatformTransactionManager.class))
            .withBean(KeycloakIdentityService.class, () -> mock(KeycloakIdentityService.class))
            .withBean(RestClient.Builder.class, RestClient::builder)
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context).hasSingleBean(GitHubActorResolver.class);
                assertThat(context).hasBean("keycloakGitHubActor");
                assertThat(context).doesNotHaveBean("localGitHubActor");
                assertThat(context).doesNotHaveBean("cognitoGitHubActor");
            });
    }
}
