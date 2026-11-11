package com.prgate.ai.infrastructure.outbound.sonarqube;

import com.fasterxml.jackson.databind.JsonNode;
import com.prgate.ai.application.exception.CatalogUnavailableException;
import com.prgate.ai.application.port.out.AnalysisCatalogPort;
import com.prgate.ai.domain.model.enums.CheckMechanism;
import com.prgate.ai.domain.model.valueobjects.*;
import com.prgate.ai.domain.policy.TechnicalCheckCatalog;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;

@Component
public final class SonarCatalogAdapter implements AnalysisCatalogPort {
    private final RestClient client;
    public SonarCatalogAdapter(RestClient.Builder builder,
            @Value("${sonar.base-url}") String url, @Value("${sonar.token:}") String token) {
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5)).build());
        factory.setReadTimeout(Duration.ofSeconds(20));
        builder = builder.clone().baseUrl(url).requestFactory(factory);
        if (!token.isBlank()) builder.defaultHeader("Authorization", "Bearer " + token);
        client = builder.build();
    }

    @Override
    public TechnicalCheckCatalog load(SourceLanguage language) {
        try {
            Set<String> keys = new TreeSet<>();
            int page = 1;
            while (true) {
                final int number = page;
                JsonNode response = client.get().uri(b -> b.path("/api/rules/search")
                        .queryParam("languages", language.value()).queryParam("p", number)
                        .queryParam("ps", 500).build()).retrieve().body(JsonNode.class);
                if (response == null || !response.path("rules").isArray()) throw new CatalogUnavailableException();
                for (var rule : response.path("rules")) {
                    if (!rule.path("key").asText().isBlank()
                            && language.value().equals(rule.path("lang").asText())
                            && !"REMOVED".equals(rule.path("status").asText())) keys.add(rule.path("key").asText());
                }
                int total = response.path("paging").path("total").asInt(response.path("total").asInt(-1));
                if (total < 0) throw new CatalogUnavailableException();
                if ((long) page * 500 >= total) break;
                if (response.path("rules").isEmpty() || ++page > 100) throw new CatalogUnavailableException();
            }
            Set<String> snapshot = Set.copyOf(keys);
            String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest((language.value() + "\n" + String.join("\n", keys)).getBytes(StandardCharsets.UTF_8)));
            String version = "sonar-rules-" + hash;
            return new TechnicalCheckCatalog() {
                public String version() { return version; }
                public boolean supports(TechnicalCheck check) {
                    return check.mechanism() == CheckMechanism.SONAR_RULE
                            && check.parameters().isEmpty() && snapshot.contains(check.target());
                }
            };
        } catch (Exception ex) { throw new CatalogUnavailableException(); }
    }
}
