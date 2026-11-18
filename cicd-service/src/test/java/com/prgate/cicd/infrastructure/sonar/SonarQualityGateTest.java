package com.prgate.cicd.infrastructure.sonar;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.prgate.cicd.infrastructure.messaging.QualityGateRequest;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class SonarQualityGateTest {
    @Test void createsAnOwnedGateAndRetriesWithoutDuplicatingConditionsOrChangingDefault() throws Exception {
        var json=new ObjectMapper(); var id=UUID.randomUUID(); String name="prgate-"+id;
        var conditions=new ArrayList<Map<String,String>>(); var created=new AtomicInteger();
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/api/qualitygates/",exchange->{
            String action=exchange.getRequestURI().getPath().substring("/api/qualitygates/".length());
            int status=200; Object response=Map.of();
            switch (action) {
                case "show" -> { if (created.get()==0) status=404; else response=Map.of("name",name,"conditions",conditions,"isBuiltIn",false); }
                case "create" -> created.incrementAndGet();
                case "create_condition" -> {
                    var form=form(new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));
                    assertEquals(name,form.get("gateName"));
                    conditions.add(Map.of("metric",form.get("metric"),"op",form.get("op"),"error",form.get("error")));
                }
                case "select" -> assertTrue(new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8).contains("projectKey=project"));
                case "get_by_project" -> response=Map.of("qualityGate",Map.of("name",name));
                default -> { status=400; }
            }
            byte[] body=json.writeValueAsBytes(response); exchange.sendResponseHeaders(status,body.length);
            try (var output=exchange.getResponseBody()) { output.write(body); }
        });
        server.start();
        try {
            var adapter=new SonarProjectHttpAdapter(json,"http://127.0.0.1:"+server.getAddress().getPort(),"test");
            var requested=List.of(new QualityGateRequest.Condition("coverage","LT",java.math.BigDecimal.valueOf(80)));
            assertEquals(name,adapter.configureQualityGate(id,"project",requested));
            assertEquals(name,adapter.configureQualityGate(id,"project",requested));
            assertEquals(1,created.get()); assertEquals(1,conditions.size());
        } finally { server.stop(0); }
    }
    private Map<String,String> form(String value) {
        Map<String,String> result=new HashMap<>();
        for (String pair:value.split("&")) { String[] fields=pair.split("=",2);
            result.put(fields[0],URLDecoder.decode(fields[1],StandardCharsets.UTF_8)); }
        return result;
    }
}
