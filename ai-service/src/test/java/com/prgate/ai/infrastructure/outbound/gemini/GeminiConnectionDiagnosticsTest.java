package com.prgate.ai.infrastructure.outbound.gemini;

import java.io.IOException;
import java.net.ConnectException;
import java.net.UnknownHostException;
import java.net.http.HttpTimeoutException;
import javax.net.ssl.SSLHandshakeException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GeminiConnectionDiagnosticsTest {
    @Test void classifiesNestedNetworkFailuresWithoutUsingSensitiveMessages() {
        assertCategory("DNS", new UnknownHostException("secret-url"));
        assertCategory("TLS_CERTIFICATE_OR_HANDSHAKE", new SSLHandshakeException("secret-key"));
        assertCategory("TIMEOUT", new HttpTimeoutException("secret-request"));
        assertCategory("CONNECTION_FAILED", new ConnectException("secret-header"));
        assertCategory("NETWORK_IO", new IOException("secret-payload"));
    }

    @Test void timeoutTakesPrecedenceOverConnectionWrapper() {
        var wrapper = new ConnectException("sensitive");
        wrapper.initCause(new HttpTimeoutException("sensitive"));
        assertEquals("TIMEOUT", GeminiClient.connectionCategory(wrapper));
    }

    @Test void handlesCyclicCauses() {
        var first = new IOException("first");
        var second = new IOException("second", first);
        first.initCause(second);
        assertEquals("NETWORK_IO", GeminiClient.connectionCategory(first));
    }

    private static void assertCategory(String expected, Throwable cause) {
        assertEquals(expected, GeminiClient.connectionCategory(new IOException("sensitive", cause)));
    }
}
