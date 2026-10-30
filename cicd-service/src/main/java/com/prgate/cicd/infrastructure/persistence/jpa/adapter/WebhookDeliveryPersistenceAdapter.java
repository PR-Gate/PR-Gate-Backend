package com.prgate.cicd.infrastructure.persistence.jpa.adapter;

import com.prgate.cicd.application.model.GitHubDeliveryId;
import com.prgate.cicd.application.port.out.WebhookDeliveryRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Objects;

@Repository
public class WebhookDeliveryPersistenceAdapter implements WebhookDeliveryRepository {

    private static final String CLAIM_DELIVERY_SQL = """
            INSERT INTO processed_webhook_deliveries (delivery_id, received_at)
            VALUES (?, ?)
            ON CONFLICT (delivery_id) DO NOTHING
            """;

    private final JdbcTemplate jdbcTemplate;

    public WebhookDeliveryPersistenceAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public boolean claimIfUnprocessed(GitHubDeliveryId deliveryId, Instant receivedAt) {
        Objects.requireNonNull(deliveryId, "GitHub delivery id is required");
        Objects.requireNonNull(receivedAt, "Webhook received time is required");
        return jdbcTemplate.update(CLAIM_DELIVERY_SQL, deliveryId.value(), receivedAt) == 1;
    }
}
