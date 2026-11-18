package com.prgate.cicd.infrastructure.sonar;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class SonarAnalysisVerificationTest {
    @Test void verifiesExactAnalysisRatherThanLatestProjectState() throws Exception {
        var gateRequestedByAnalysis = new AtomicBoolean();
        var server = server("b".repeat(40), gateRequestedByAnalysis);
        try {
            var adapter = adapter(server);
            var result = adapter.verifyCompletedAnalysis("task", "project", "b".repeat(40));
            assertEquals("analysis", result.analysisId());
            assertEquals("ERROR", result.qualityGateStatus());
            assertTrue(gateRequestedByAnalysis.get());
        } finally { server.stop(0); }
    }

    @Test void refusesRevisionMismatchBeforeReadingGate() throws Exception {
        var gateRequested = new AtomicBoolean();
        var server = server("c".repeat(40), gateRequested);
        try {
            assertThrows(IllegalStateException.class,
                    () -> adapter(server).verifyCompletedAnalysis("task", "project", "b".repeat(40)));
            assertFalse(gateRequested.get());
        } finally { server.stop(0); }
    }

    private SonarProjectHttpAdapter adapter(HttpServer server) {
        return new SonarProjectHttpAdapter(new ObjectMapper(),
                "http://127.0.0.1:" + server.getAddress().getPort(), "test-token");
    }

    private HttpServer server(String revision, AtomicBoolean gateRequested) throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            String body;
            if (path.equals("/api/ce/task")) {
                body = "{\"task\":{\"status\":\"SUCCESS\",\"componentKey\":\"project\",\"analysisId\":\"analysis\"}}";
            } else if (path.equals("/api/project_analyses/search")) {
                body = "{\"analyses\":[{\"key\":\"newer\",\"revision\":\"newer-revision\"},"
                        + "{\"key\":\"analysis\",\"revision\":\"" + revision + "\"}]}";
            } else {
                gateRequested.set("analysisId=analysis".equals(exchange.getRequestURI().getQuery()));
                body = "{\"projectStatus\":{\"status\":\"ERROR\"}}";
            }
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            try (var output = exchange.getResponseBody()) { output.write(bytes); }
        });
        server.start();
        return server;
    }
}
