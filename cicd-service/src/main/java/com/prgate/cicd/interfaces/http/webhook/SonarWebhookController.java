package com.prgate.cicd.interfaces.http.webhook;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.ResponseEntity;
import com.prgate.cicd.application.port.out.SonarWebhookInboxPort;
import com.prgate.cicd.application.port.out.SonarWebhookReceiptPort;
import org.springframework.web.bind.annotation.*;

/** The signature filter authenticates the raw body before this durable inbox insert. */
@RestController
@RequestMapping("/api/v1/webhooks/sonar")
public class SonarWebhookController {
    private final SonarWebhookInboxPort inbox;
    private final ObjectMapper json;
    private final SonarWebhookReceiptPort receipts;
    public SonarWebhookController(SonarWebhookInboxPort inbox, ObjectMapper json, SonarWebhookReceiptPort receipts) {
        this.inbox = inbox; this.json = json; this.receipts = receipts;
    }

    @PostMapping
    public ResponseEntity<Receipt> receive(@RequestBody byte[] body) {
        final com.fasterxml.jackson.databind.JsonNode payload;
        try { payload = json.readTree(body); }
        catch (java.io.IOException exception) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Invalid Sonar JSON");
        }
        if (payload == null || !payload.isObject()) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Sonar payload must be an object");
        }
        String projectKey = payload.path("project").path("key").asText();
        String status = payload.path("status").asText();
        if (projectKey.isBlank() || status.isBlank()) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST,
                    "Sonar project key and task status are required");
        }
        String taskId = payload.path("taskId").asText("");
        boolean firstDelivery = receipts.record(body, projectKey, taskId.isBlank() ? null : taskId, status);
        // Receipt is not proof of a correlated pipeline result.
        var properties = payload.path("properties");
        if (!"SUCCESS".equals(status)
                || (properties.path("sonar.analysis.prgateRunId").asText().isBlank()
                && properties.path("sonar.analysis.prgateWorkflowRunId").asText().isBlank())) {
            return ResponseEntity.accepted().body(new Receipt("SONAR_RECEIVED_UNCORRELATED", projectKey, taskId, !firstDelivery));
        }
        inbox.enqueue(body);
        return ResponseEntity.accepted().body(new Receipt("SONAR_QUEUED", projectKey, taskId, !firstDelivery));
    }

    public record Receipt(String result, String projectKey, String taskId, boolean duplicate) { }
}
