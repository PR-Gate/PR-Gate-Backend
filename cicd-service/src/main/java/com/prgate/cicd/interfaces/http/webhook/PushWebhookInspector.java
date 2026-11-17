package com.prgate.cicd.interfaces.http.webhook;

import com.fasterxml.jackson.databind.JsonNode;
import com.prgate.cicd.application.port.out.PushReceiptPort;
import com.prgate.cicd.domain.model.valueobject.GitCommitSha;
import com.prgate.cicd.domain.model.valueobject.GitHubRepository;
import org.springframework.stereotype.Component;
import java.util.UUID;

/** Receives signed push metadata; no scanner or academic enrollment is invoked. */
@Component
public class PushWebhookInspector {
    private final PushReceiptPort receipts;
    public PushWebhookInspector(PushReceiptPort receipts) { this.receipts = receipts; }

    public Receipt inspect(String deliveryId, JsonNode payload) {
        UUID delivery = UUID.fromString(deliveryId);
        JsonNode repository = payload.path("repository");
        var github = new GitHubRepository(repository.path("id").asLong(),
                text(repository.path("owner"), "login"), text(repository, "name"));
        String ref = text(payload, "ref");
        if (ref.length() > 1024 || !(ref.startsWith("refs/heads/") || ref.startsWith("refs/tags/"))) {
            throw new IllegalArgumentException("Invalid Git ref");
        }
        String before = new GitCommitSha(text(payload,"before")).value();
        String after = new GitCommitSha(text(payload,"after")).value();
        if (!payload.path("deleted").isBoolean() || !payload.path("commits").isArray()) {
            throw new IllegalArgumentException("Invalid push metadata");
        }
        boolean deleted = payload.path("deleted").asBoolean();
        int commits = payload.path("commits").size();
        boolean recorded = receipts.record(delivery, github.id(), github.owner(), github.name(),
                ref, before, after, deleted, commits);
        return new Receipt(recorded ? "PUSH_RECEIVED" : "DUPLICATE_PUSH", delivery,
                github.id(), github.fullName(), ref, before, after, deleted, commits, false);
    }

    private static String text(JsonNode node, String key) {
        if (!node.path(key).isTextual() || node.path(key).asText().isBlank()) {
            throw new IllegalArgumentException("Missing push field: " + key);
        }
        return node.path(key).asText();
    }

    public record Receipt(String result, UUID deliveryId, long repositoryId, String repository,
                          String ref, String before, String after, boolean deleted,
                          int commitsInPayload, boolean analysisStarted) { }
}
