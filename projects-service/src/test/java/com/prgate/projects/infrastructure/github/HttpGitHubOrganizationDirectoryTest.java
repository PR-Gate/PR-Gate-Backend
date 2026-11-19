package com.prgate.projects.infrastructure.github;

import com.prgate.projects.application.exception.GitHubDirectoryException;
import com.prgate.projects.domain.model.valueobject.GitHubOrganization;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

class HttpGitHubOrganizationDirectoryTest {
    private MockRestServiceServer server;
    private HttpGitHubOrganizationDirectory directory;

    @BeforeEach
    void setup() {
        var builder = RestClient.builder().baseUrl("https://api.github.com");
        builder.messageConverters(converters -> {
            converters.removeIf(MappingJackson2HttpMessageConverter.class::isInstance);
            converters.add(new MappingJackson2HttpMessageConverter(
                    new ObjectMapper().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)));
        });
        server = MockRestServiceServer.bindTo(builder).build();
        directory = new HttpGitHubOrganizationDirectory(builder.build());
    }

    @Test
    void returnsVerifiedCanonicalMetadata() {
        server.expect(requestTo("https://api.github.com/orgs/prgate"))
                .andRespond(withSuccess("""
                        {"id":42,"login":"PRGate","type":"Organization","public_repos":0,"description":null}
                        """, MediaType.APPLICATION_JSON));
        assertThat(directory.findByLogin(" PRGate ")).contains(new GitHubOrganization(42, "prgate"));
        server.verify();
    }

    @Test
    void returnsEmptyOnlyFor404() {
        server.expect(requestTo("https://api.github.com/orgs/missing")).andRespond(withStatus(HttpStatusCode.valueOf(404)));
        assertThat(directory.findByLogin("missing")).isEmpty();
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(ints = {401, 403, 429, 500, 503})
    void translatesRemoteFailuresWithoutReportingOrganizationMissing(int status) {
        server.expect(requestTo("https://api.github.com/orgs/prgate")).andRespond(withStatus(HttpStatusCode.valueOf(status)));
        assertThatThrownBy(() -> directory.findByLogin("prgate")).isInstanceOf(GitHubDirectoryException.class);
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"id\":42,\"login\":\"prgate\",\"type\":\"User\"}",
            "{\"id\":0,\"login\":\"prgate\",\"type\":\"Organization\"}", "malformed"})
    void rejectsMalformedResponses(String response) {
        server.expect(requestTo("https://api.github.com/orgs/prgate")).andRespond(withSuccess(response, MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> directory.findByLogin("prgate")).isInstanceOf(GitHubDirectoryException.class);
        server.verify();
    }

    @Test
    void rejectsUrlsBeforeNetworkAccess() {
        assertThatThrownBy(() -> directory.findByLogin("https://example.com"))
                .isInstanceOf(IllegalArgumentException.class);
        server.verify();
    }
}
