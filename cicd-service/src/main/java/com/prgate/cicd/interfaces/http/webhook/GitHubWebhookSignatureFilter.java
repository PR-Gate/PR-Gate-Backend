package com.prgate.cicd.interfaces.http.webhook;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;

/** Verifies GitHub's HMAC-SHA256 signature before the webhook reaches the controller. */
@Component
public class GitHubWebhookSignatureFilter extends OncePerRequestFilter {

    private static final String WEBHOOK_PATH = "/api/v1/webhooks/github";
    private static final String SIGNATURE_HEADER = "X-Hub-Signature-256";
    private static final String SHA256_PREFIX = "sha256=";
    private static final int MAX_BODY_SIZE_BYTES = 5 * 1024 * 1024;

    private final String webhookSecret;

    public GitHubWebhookSignatureFilter(
            @Value("${app.github.webhook-secret:}") String webhookSecret) {
        this.webhookSecret = webhookSecret;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !WEBHOOK_PATH.equals(request.getServletPath());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (webhookSecret == null || webhookSecret.isBlank()) {
            writeError(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                    "GitHub webhook secret is not configured");
            return;
        }

        byte[] requestBody = request.getInputStream().readNBytes(MAX_BODY_SIZE_BYTES + 1);
        if (requestBody.length > MAX_BODY_SIZE_BYTES) {
            writeError(response, HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE,
                    "GitHub webhook payload exceeds the maximum supported size");
            return;
        }
        if (!isValidSignature(request.getHeader(SIGNATURE_HEADER), requestBody)) {
            writeError(response, HttpServletResponse.SC_UNAUTHORIZED,
                    "GitHub webhook signature is missing or invalid");
            return;
        }

        filterChain.doFilter(new CachedBodyRequest(request, requestBody), response);
    }

    private boolean isValidSignature(String signatureHeader, byte[] requestBody) {
        if (signatureHeader == null || !signatureHeader.startsWith(SHA256_PREFIX)) {
            return false;
        }

        final byte[] receivedSignature;
        try {
            receivedSignature = HexFormat.of().parseHex(signatureHeader.substring(SHA256_PREFIX.length()));
        } catch (IllegalArgumentException exception) {
            return false;
        }
        if (receivedSignature.length != 32) {
            return false;
        }

        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] expectedSignature = mac.doFinal(requestBody);
            return MessageDigest.isEqual(expectedSignature, receivedSignature);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("HMAC-SHA256 is unavailable", exception);
        }
    }

    private void writeError(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"error\":\"" + message + "\"}");
    }

    private static final class CachedBodyRequest extends HttpServletRequestWrapper {

        private final byte[] body;

        private CachedBodyRequest(HttpServletRequest request, byte[] body) {
            super(request);
            this.body = body;
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream input = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override
                public int read() {
                    return input.read();
                }

                @Override
                public boolean isFinished() {
                    return input.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener readListener) {
                    try {
                        if (!isFinished()) {
                            readListener.onDataAvailable();
                        }
                        if (isFinished()) {
                            readListener.onAllDataRead();
                        }
                    } catch (IOException exception) {
                        readListener.onError(exception);
                    }
                }
            };
        }

        @Override
        public BufferedReader getReader() {
            return new BufferedReader(new InputStreamReader(getInputStream(), getCharacterEncoding() == null
                    ? StandardCharsets.UTF_8
                    : java.nio.charset.Charset.forName(getCharacterEncoding())));
        }
    }
}
