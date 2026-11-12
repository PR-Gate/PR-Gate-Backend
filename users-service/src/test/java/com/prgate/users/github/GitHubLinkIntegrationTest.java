package com.prgate.users.github;

import com.fasterxml.jackson.databind.*;
import com.prgate.users.application.github.*;
import com.prgate.users.application.internal.commandservices.UserCommandService;
import com.prgate.users.domain.model.*;
import com.prgate.users.infraestructure.github.*;
import com.prgate.users.infraestructure.persistence.jpa.repositories.UserRepository;
import com.sun.net.httpserver.*;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.*;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.*;
import org.springframework.web.client.RestClient;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.oauth2.jwt.*;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.interfaces.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
    "spring.config.import=", "github.enabled=true", "github.client-id=fixture-client",
    "prgate.internal.service-token=github-fixture-internal-token",
    "github.token-encryption-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
    "github.client-secret=fixture-secret", "github.redirect-uri=http://localhost:8080/api/integrations/github/callback",
    "spring.datasource.url=jdbc:h2:mem:github-tests;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "logging.level.root=INFO", "debug=false"
})
@AutoConfigureMockMvc
@ActiveProfiles({"test", "local"})
class GitHubLinkIntegrationTest {
    private static final HttpServer SERVER = server();
    private static final AtomicInteger CALLS = new AtomicInteger();
    private static final AtomicReference<Map<String,String>> LAST_FORM = new AtomicReference<>();
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired UserCommandService users;
    @Autowired UserRepository repository;
    @Autowired JdbcTemplate jdbc;
    @Autowired GitHubLinkStore store;

    @TestConfiguration
    static class Fixture {
        @Bean @Primary GitHubIdentityProvider fixtureProvider() {
            String endpoint = "http://localhost:" + SERVER.getAddress().getPort();
            return new HttpGitHubIdentityProvider(RestClient.builder(), "fixture-client", "fixture-secret",
                    "http://localhost:8080/api/integrations/github/callback", endpoint, endpoint);
        }
    }
    @AfterAll static void stopServer() { SERVER.stop(0); }

    @Test void authorizationUsesPkceBrowserCookieAndStoresOnlyHashOfState() throws Exception {
        UUID user = register();
        var flow = begin(user);
        assertEquals("S256", flow.query.get("code_challenge_method"));
        assertEquals("fixture-client", flow.query.get("client_id"));
        assertFalse(flow.query.containsKey("scope"));
        assertEquals(43, flow.state.length());
        assertTrue(flow.cookieHeader.contains("HttpOnly"));
        assertTrue(flow.cookieHeader.contains("SameSite=Lax"));
        assertFalse(flow.cookieHeader.contains("Secure"));
        String storedHash = jdbc.queryForObject("SELECT state_hash FROM users.github_authorizations WHERE user_id=?", String.class, user);
        assertEquals(GitHubLinkService.digest(flow.state), storedHash);
        assertNotEquals(flow.state, storedHash);
        String verifier = jdbc.queryForObject("SELECT verifier FROM users.github_authorizations WHERE user_id=?", String.class, user);
        assertEquals(GitHubLinkService.digest(verifier), flow.query.get("code_challenge"));
    }

    @Test void linksIdentityRetrievesAndUnlinksWithoutExposingTokens() throws Exception {
        UUID user = register(); var flow = begin(user);
        callback(flow, "id-101").andExpect(status().isOk()).andExpect(jsonPath("$.githubUserId").value(101))
                .andExpect(jsonPath("$.login").value("student-101"))
                .andExpect(jsonPath("$.access_token").doesNotExist())
                .andExpect(header().string("Cache-Control", "no-store"));
        assertEquals("fixture-secret", LAST_FORM.get().get("client_secret"));
        String encrypted=jdbc.queryForObject("SELECT access_token_ciphertext FROM users.github_account_links WHERE user_id=?",String.class,user);
        assertNotNull(encrypted); assertFalse(encrypted.contains("id-101"));
        assertEquals(flow.query.get("code_challenge"),
                GitHubLinkService.digest(LAST_FORM.get().get("code_verifier")));
        mvc.perform(get("/api/me/integrations/github").header("X-User-Id", user.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.userId").value(user.toString()));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM users.github_authorizations WHERE user_id=?", Integer.class, user));
        mvc.perform(delete("/api/me/integrations/github").header("X-User-Id", user.toString()))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/me/integrations/github").header("X-User-Id", user.toString()))
                .andExpect(status().isNotFound());
    }

    @Test void missingWrongAndReplayedStateNeverMakeASecondProviderCall() throws Exception {
        var flow = begin(register()); int before = CALLS.get();
        mvc.perform(get("/api/integrations/github/callback").param("code", "id-102"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/integrations/github/callback").cookie(new Cookie("prgate_github_link", "x".repeat(43)))
                .param("state", flow.state).param("code", "id-102")).andExpect(status().isBadRequest());
        assertEquals(before, CALLS.get());
        callback(flow, "id-102").andExpect(status().isOk());
        int after = CALLS.get();
        callback(flow, "id-102").andExpect(status().isBadRequest());
        assertEquals(after, CALLS.get());
    }

    @Test void expiredStateIsRejectedBeforeProviderCall() throws Exception {
        UUID user = register(); var flow = begin(user);
        jdbc.update("UPDATE users.github_authorizations SET expires_at=? WHERE user_id=?",
                java.sql.Timestamp.from(Instant.now().minusSeconds(1)), user);
        int before = CALLS.get();
        callback(flow, "id-103").andExpect(status().isBadRequest());
        assertEquals(before, CALLS.get());
    }

    @Test void cancelledAuthorizationIsConsumedAndDoesNotCallProvider() throws Exception {
        var flow = begin(register()); int before = CALLS.get();
        mvc.perform(get("/api/integrations/github/callback").cookie(flow.cookie).param("state", flow.state)
                .param("error", "access_denied")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("GITHUB_AUTHORIZATION_CANCELLED"));
        callback(flow, "id-104").andExpect(status().isBadRequest());
        assertEquals(before, CALLS.get());
    }

    @Test void providerFailureReturnsSafeErrorAndRequiresNewAuthorization() throws Exception {
        var flow = begin(register());
        String body = callback(flow, "fail").andExpect(status().isBadGateway()).andReturn().getResponse().getContentAsString();
        assertTrue(body.contains("GITHUB_PROVIDER_UNAVAILABLE"));
        assertFalse(body.contains("provider-sensitive"));
        assertFalse(body.contains("fixture-secret"));
        callback(flow, "id-105").andExpect(status().isBadRequest());
    }

    @Test void identityCannotBeLinkedToTwoPlatformAccounts() throws Exception {
        UUID first = register(); UUID second = register();
        callback(begin(first), "id-106").andExpect(status().isOk());
        callback(begin(second), "id-106").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GITHUB_IDENTITY_IN_USE"));
        mvc.perform(post("/api/me/integrations/github/authorization").header("X-User-Id", first.toString()))
                .andExpect(status().isOk());
        assertTrue(store.find(second).isEmpty());
    }

    @Test void newAuthorizationInvalidatesOldFlow() throws Exception {
        UUID user = register(); var first = begin(user); var second = begin(user);
        callback(first, "id-107").andExpect(status().isBadRequest());
        callback(second, "id-107").andExpect(status().isOk());
    }

    @Test void unlinkInvalidatesAnAuthorizationAlreadyBeingProcessed() throws Exception {
        UUID user = register(); var flow = begin(user);
        var pending = store.consume(GitHubLinkService.digest(flow.state), GitHubLinkService.digest(flow.cookie.getValue()), Instant.now());
        store.unlink(user);
        assertThrows(GitHubLinkException.class, () -> store.finish(pending,
                new GitHubIdentityProvider.Identity(108, "student-108"), Instant.now()));
        assertTrue(store.find(user).isEmpty());
    }

    @Test void onlyOneConsumerCanClaimAState() throws Exception {
        var flow = begin(register());
        var executor = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var start = new java.util.concurrent.CountDownLatch(1);
            java.util.concurrent.Callable<Boolean> consume = () -> {
                start.await();
                try { store.consume(GitHubLinkService.digest(flow.state), GitHubLinkService.digest(flow.cookie.getValue()), Instant.now()); return true; }
                catch (GitHubLinkException rejected) { return false; }
            };
            var a = executor.submit(consume); var b = executor.submit(consume); start.countDown();
            assertNotEquals(a.get(), b.get());
        } finally { executor.shutdownNow(); }
    }

    @Test void requiresActiveLocalUserAndNeverAcceptsUserIdFromRequestBody() throws Exception {
        mvc.perform(post("/api/me/integrations/github/authorization")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/me/integrations/github").header("X-User-Id", "invalid")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/me/integrations/github/authorization").contentType("application/json")
                .content("{\"userId\":\"" + register() + "\"}")).andExpect(status().isUnauthorized());
        UUID inactive = register();
        users.changeUserStatus(inactive, UserStatus.INACTIVE, Instant.now().plusSeconds(1));
        mvc.perform(post("/api/me/integrations/github/authorization").header("X-User-Id", inactive.toString()))
                .andExpect(status().isForbidden());
    }

    @Test void inactiveUserCannotCompleteAnEarlierAuthorization() throws Exception {
        UUID inactive = register(); var flow = begin(inactive);
        users.changeUserStatus(inactive, UserStatus.INACTIVE, Instant.now().plusSeconds(1));
        callback(flow, "id-109").andExpect(status().isForbidden());
        assertTrue(store.find(inactive).isEmpty());
    }

    @Test void cognitoRequiresValidSignatureIssuerAccessTokenClientAndExpiration() throws Exception {
        UUID user = register();
        var pair = KeyPairGenerator.getInstance("RSA"); pair.initialize(2048);
        var key = pair.generateKeyPair();
        var decoder = NimbusJwtDecoder.withPublicKey((RSAPublicKey) key.getPublic()).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer("https://issuer.example"));
        var resolver = new CognitoGitHubActorResolver(repository, decoder, "app-client");
        String sub = repository.findById(user).orElseThrow().getCognitoSub();
        assertEquals(user, resolver.resolve(request(signed(key, sub, "https://issuer.example", "access", "app-client", true))));
        for (String token : List.of(
                signed(key, sub, "https://other.example", "access", "app-client", true),
                signed(key, sub, "https://issuer.example", "id", "app-client", true),
                signed(key, sub, "https://issuer.example", "access", "wrong-client", true),
                signed(key, sub, "https://issuer.example", "access", "app-client", false),
                "invalid.signature.value")) {
            var failure = assertThrows(GitHubLinkException.class, () -> resolver.resolve(request(token)));
            assertEquals(GitHubLinkException.Reason.AUTHENTICATION_REQUIRED, failure.reason());
        }
        var noBearer = new MockHttpServletRequest(); noBearer.addHeader("X-User-Id", user.toString());
        assertThrows(GitHubLinkException.class, () -> resolver.resolve(noBearer));
        var otherKey = pair.generateKeyPair();
        assertThrows(GitHubLinkException.class, () -> resolver.resolve(request(
                signed(otherKey, sub, "https://issuer.example", "access", "app-client", true))));
        var noExpiry = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), new JWTClaimsSet.Builder()
                .issuer("https://issuer.example").subject(sub).claim("token_use", "access")
                .claim("client_id", "app-client").build());
        noExpiry.sign(new RSASSASigner((RSAPrivateKey) key.getPrivate()));
        assertThrows(GitHubLinkException.class, () -> resolver.resolve(request(noExpiry.serialize())));
    }

    private String signed(KeyPair pair, String sub, String issuer, String tokenUse, String client, boolean validTime) throws Exception {
        Instant now = Instant.now();
        var claims = new JWTClaimsSet.Builder().issuer(issuer).subject(sub)
                .issueTime(Date.from(now.minusSeconds(120)))
                .expirationTime(Date.from(validTime ? now.plusSeconds(300) : now.minusSeconds(120)))
                .claim("token_use", tokenUse).claim("client_id", client).build();
        var jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims);
        jwt.sign(new RSASSASigner((RSAPrivateKey) pair.getPrivate()));
        return jwt.serialize();
    }
    private MockHttpServletRequest request(String token) {
        var request = new MockHttpServletRequest(); request.addHeader("Authorization", "Bearer " + token); return request;
    }
    private UUID register() {
        UUID id = UUID.randomUUID();
        users.registerUser(id, "test-sub-" + id, "Student", "Example", null, Instant.now());
        return id;
    }
    private Flow begin(UUID user) throws Exception {
        var response = mvc.perform(post("/api/me/integrations/github/authorization").header("X-User-Id", user.toString()))
                .andExpect(status().isOk()).andReturn().getResponse();
        var json = mapper.readTree(response.getContentAsString());
        assertFalse(json.has("browserSecret"));
        var query = parse(URI.create(json.path("authorizationUrl").asText()).getRawQuery());
        String setCookie = response.getHeader("Set-Cookie");
        String value = setCookie.substring(setCookie.indexOf('=') + 1, setCookie.indexOf(';'));
        return new Flow(query.get("state"), new Cookie("prgate_github_link", value), setCookie, query);
    }
    private ResultActions callback(Flow flow, String code) throws Exception {
        return mvc.perform(get("/api/integrations/github/callback").cookie(flow.cookie).param("state", flow.state).param("code", code));
    }
    private record Flow(String state, Cookie cookie, String cookieHeader, Map<String,String> query) {}
    private static Map<String,String> parse(String encoded) {
        var result = new HashMap<String,String>();
        for (String field : encoded.split("&")) {
            String[] pair = field.split("=", 2);
            result.put(URLDecoder.decode(pair[0], StandardCharsets.UTF_8), URLDecoder.decode(pair[1], StandardCharsets.UTF_8));
        }
        return result;
    }
    private static HttpServer server() {
        try {
            var server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
            server.createContext("/login/oauth/access_token", exchange -> {
                CALLS.incrementAndGet();
                var form = parse(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                LAST_FORM.set(form);
                if ("fail".equals(form.get("code"))) { respond(exchange, 500, "{\"error\":\"provider-sensitive\"}"); return; }
                respond(exchange, 200, "{\"access_token\":\"" + form.get("code") + "\",\"token_type\":\"bearer\"}");
            });
            server.createContext("/user", exchange -> {
                String token = exchange.getRequestHeaders().getFirst("Authorization").substring("Bearer id-".length());
                respond(exchange, 200, "{\"id\":" + token + ",\"login\":\"student-" + token + "\"}");
            });
            server.start(); return server;
        } catch (Exception error) { throw new IllegalStateException(error); }
    }
    private static void respond(HttpExchange exchange, int status, String body) throws java.io.IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (var output = exchange.getResponseBody()) { output.write(bytes); }
    }
}
