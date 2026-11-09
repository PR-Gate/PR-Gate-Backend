package com.prgate.cicd.interfaces.http.webhook;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import static org.junit.jupiter.api.Assertions.*;

class WebhookSignatureFilterTest {
    @Test
    void validSonarSignaturePreservesOriginalBodyForController() throws Exception {
        byte[] body = "{\"status\":\"SUCCESS\"}".getBytes(StandardCharsets.UTF_8);
        var request = request(body);
        request.addHeader("X-Sonar-Webhook-HMAC-SHA256", signature("sonar-secret", body));
        var response = new MockHttpServletResponse();
        var chain = new MockFilterChain();
        new GitHubWebhookSignatureFilter("github-secret", "sonar-secret").doFilter(request, response, chain);
        assertNotNull(chain.getRequest());
        assertArrayEquals(body, chain.getRequest().getInputStream().readAllBytes());
    }

    @Test
    void githubSecretCannotAuthenticateSonarWebhook() throws Exception {
        byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
        var request = request(body);
        request.addHeader("X-Sonar-Webhook-HMAC-SHA256", signature("github-secret", body));
        var response = new MockHttpServletResponse();
        var chain = new MockFilterChain();
        new GitHubWebhookSignatureFilter("github-secret", "sonar-secret").doFilter(request, response, chain);
        assertEquals(401, response.getStatus());
        assertNull(chain.getRequest());
    }

    private MockHttpServletRequest request(byte[] body) {
        var request = new MockHttpServletRequest("POST", "/api/v1/webhooks/sonar");
        request.setServletPath("/api/v1/webhooks/sonar");
        request.setContent(body);
        return request;
    }
    private String signature(String secret, byte[] body) throws Exception {
        var mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(body));
    }
}
