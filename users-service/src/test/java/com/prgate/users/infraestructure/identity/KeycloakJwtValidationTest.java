package com.prgate.users.infraestructure.identity;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jwt.*;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.springframework.security.oauth2.jwt.*;
import java.net.*;
import java.security.*;
import java.security.interfaces.*;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class KeycloakJwtValidationTest {
    static final KeyPair KEY = key();
    static final HttpServer SERVER = server();
    static final String ISSUER = "http://localhost:8090/realms/prgate";
    final JwtDecoder decoder = new KeycloakIdentityConfiguration().keycloakDecoder(ISSUER,
            "http://localhost:" + SERVER.getAddress().getPort() + "/certs", "prgate-api", "prgate-frontend");
    @AfterAll static void stop() { SERVER.stop(0); }
    @Test void acceptsSignedKeycloakAccessToken() throws Exception {
        assertEquals("subject", decoder.decode(token(KEY, ISSUER, "prgate-api", "prgate-frontend", "Bearer", 300)).getSubject());
    }
    @Test void rejectsWrongSignatureIssuerAudienceClientTypeAndExpiry() throws Exception {
        for (String token : List.of(
                token(key(), ISSUER, "prgate-api", "prgate-frontend", "Bearer", 300),
                token(KEY, "http://other.example/realm", "prgate-api", "prgate-frontend", "Bearer", 300),
                token(KEY, ISSUER, "wrong-api", "prgate-frontend", "Bearer", 300),
                token(KEY, ISSUER, "prgate-api", "wrong-client", "Bearer", 300),
                token(KEY, ISSUER, "prgate-api", "prgate-frontend", "ID", 300),
                token(KEY, ISSUER, "prgate-api", "prgate-frontend", "Bearer", -300))) {
            assertThrows(JwtException.class, () -> decoder.decode(token));
        }
    }
    static String token(KeyPair key, String issuer, String aud, String client, String type, int seconds) throws Exception {
        var claims = new JWTClaimsSet.Builder().issuer(issuer).subject("subject").audience(aud)
                .issueTime(new Date()).expirationTime(Date.from(Instant.now().plusSeconds(seconds)))
                .claim("azp", client).claim("typ", type).build();
        var jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("fixture").build(), claims);
        jwt.sign(new RSASSASigner((RSAPrivateKey) key.getPrivate())); return jwt.serialize();
    }
    static KeyPair key() {
        try { var generator = KeyPairGenerator.getInstance("RSA"); generator.initialize(2048); return generator.generateKeyPair(); }
        catch (Exception error) { throw new IllegalStateException(error); }
    }
    static HttpServer server() {
        try {
            var server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
            var key = new com.nimbusds.jose.jwk.RSAKey.Builder((RSAPublicKey) KEY.getPublic()).keyID("fixture").build();
            byte[] body = new JWKSet(key).toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
            server.createContext("/certs", exchange -> {
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                try (var out = exchange.getResponseBody()) { out.write(body); }
            });
            server.start(); return server;
        } catch (Exception error) { throw new IllegalStateException(error); }
    }
}
