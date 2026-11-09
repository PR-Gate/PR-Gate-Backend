package com.prgate.cicd.interfaces.http.webhook;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.ResponseEntity;
import com.prgate.cicd.application.port.out.SonarWebhookInboxPort;
import org.springframework.web.bind.annotation.*;

/** The signature filter authenticates the raw body before this durable inbox insert. */
@RestController
@RequestMapping("/api/v1/webhooks/sonar")
public class SonarWebhookController {
    private final SonarWebhookInboxPort inbox;
    private final ObjectMapper json;
    public SonarWebhookController(SonarWebhookInboxPort inbox, ObjectMapper json) { this.inbox = inbox; this.json = json; }

    @PostMapping
    public ResponseEntity<Void> receive(@RequestBody byte[] body) {
        final com.fasterxml.jackson.databind.JsonNode payload;
        try { payload = json.readTree(body); }
        catch (java.io.IOException exception) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Invalid Sonar JSON");
        }
        if (payload == null || !payload.isObject()) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Sonar payload must be an object");
        }
        // Quality gate recalculations and analyses outside PRGate are not correlated here.
        if (!"SUCCESS".equals(payload.path("status").asText())
                || payload.path("properties").path("sonar.analysis.prgateRunId").asText().isBlank()) {
            return ResponseEntity.accepted().build();
        }
        inbox.enqueue(body);
        return ResponseEntity.accepted().build();
    }
}
