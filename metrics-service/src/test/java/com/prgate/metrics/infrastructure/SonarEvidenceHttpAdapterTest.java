package com.prgate.metrics.infrastructure;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.prgate.metrics.application.dto.AnalysisCompletedEvent;
import com.prgate.metrics.application.port.out.SonarEvidencePort;
import com.prgate.metrics.infrastructure.outbound.sonarqube.SonarEvidenceHttpAdapter;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class SonarEvidenceHttpAdapterTest {
    @Test void collectsOnlyTheExactHistoryTimestampAndDoesNotInventMissingValues() throws Exception {
        var calls=new AtomicInteger(); var server=server(false,calls);
        try {
            var adapter=new SonarEvidenceHttpAdapter(new ObjectMapper(),"http://127.0.0.1:"+server.getAddress().getPort(),"test");
            var result=adapter.collect(event());
            assertEquals(0,result.get("coverage").compareTo(java.math.BigDecimal.valueOf(90)));
            assertFalse(result.containsKey("vulnerabilities")); assertEquals(1,calls.get());
        } finally { server.stop(0); }
    }
    @Test void sameSecondAnalysesAreAmbiguousAndMustNotReceiveHistoricalMetrics() throws Exception {
        var calls=new AtomicInteger(); var server=server(true,calls);
        try {
            var adapter=new SonarEvidenceHttpAdapter(new ObjectMapper(),"http://127.0.0.1:"+server.getAddress().getPort(),"test");
            assertThrows(SonarEvidencePort.EvidenceUnavailable.class,()->adapter.collect(event()));
            assertEquals(0,calls.get());
        } finally { server.stop(0); }
    }
    private HttpServer server(boolean ambiguous,AtomicInteger historyCalls) throws Exception {
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/api/",exchange->{
            String body;
            if (exchange.getRequestURI().getPath().equals("/api/ce/task"))
                body="{\"task\":{\"status\":\"SUCCESS\",\"componentKey\":\"project\",\"analysisId\":\"analysis\"}}";
            else if (exchange.getRequestURI().getPath().equals("/api/project_analyses/search"))
                body="{\"paging\":{\"total\":"+(ambiguous?2:1)+"},\"analyses\":[{\"key\":\"analysis\",\"revision\":\""+"a".repeat(40)
                        +"\",\"date\":\"2026-11-17T00:49:03+0000\"}"+(ambiguous?",{\"key\":\"other\",\"date\":\"2026-11-17T00:49:03+0000\"}":"")+"]}";
            else {
                historyCalls.incrementAndGet();
                body="""
                        {"paging":{"total":2},"measures":[{"metric":"coverage","history":[
                          {"date":"2026-11-17T00:49:03+0000","value":"90"},
                          {"date":"2026-11-17T00:50:03+0000","value":"12"}]}]}
                        """;
            }
            byte[] bytes=body.getBytes(StandardCharsets.UTF_8); exchange.sendResponseHeaders(200,bytes.length);
            try (var output=exchange.getResponseBody()) { output.write(bytes); }
        });
        server.start(); return server;
    }
    private AnalysisCompletedEvent event() {
        return new AnalysisCompletedEvent(1,AnalysisCompletedEvent.TYPE,UUID.randomUUID(),Instant.now(),123,
                "example/backend","refs/heads/main","a".repeat(40),456,1,"project","task","analysis",
                Instant.parse("2026-11-17T00:49:03Z"),"OK",Map.of());
    }
}
