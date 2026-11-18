package com.prgate.cicd.infrastructure.messaging;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Component
@ConditionalOnProperty(name = "app.messaging.enabled", havingValue = "true")
public class AnalysisOutboxPublisher {
    private static final String EXCHANGE = "prgate.events";
    private final JdbcTemplate jdbc;
    private final RabbitTemplate rabbit;

    public AnalysisOutboxPublisher(JdbcTemplate jdbc, RabbitTemplate rabbit) {
        this.jdbc = jdbc;
        this.rabbit = rabbit;
    }

    @Bean
    TopicExchange analysisEventsExchange() { return new TopicExchange(EXCHANGE, true, false); }

    @Scheduled(fixedDelayString = "${app.messaging.publish-delay-ms:5000}")
    public void publishPending() {
        var rows = jdbc.query("""
                SELECT event_id, payload, routing_key FROM cicd.analysis_event_outbox
                WHERE published_at IS NULL AND next_attempt_at <= CURRENT_TIMESTAMP
                  AND (lease_until IS NULL OR lease_until < CURRENT_TIMESTAMP)
                ORDER BY created_at LIMIT 10
                """, (row, index) -> new Pending(row.getObject(1, UUID.class), row.getString(2), row.getString(3)));
        for (var row : rows) publish(row);
    }

    private void publish(Pending event) {
        int claimed = jdbc.update("""
                UPDATE cicd.analysis_event_outbox SET lease_until = CURRENT_TIMESTAMP + INTERVAL '60 seconds'
                WHERE event_id = ? AND published_at IS NULL AND next_attempt_at <= CURRENT_TIMESTAMP
                  AND (lease_until IS NULL OR lease_until < CURRENT_TIMESTAMP)
                """, event.id());
        if (claimed != 1) return;
        try {
            var correlation = new CorrelationData(event.id().toString());
            var message = MessageBuilder.withBody(event.payload().getBytes(StandardCharsets.UTF_8))
                    .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                    .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
                    .setMessageId(event.id().toString()).build();
            rabbit.send(EXCHANGE, event.routingKey(), message, correlation);
            var confirmation = correlation.getFuture().get(10, TimeUnit.SECONDS);
            if (!confirmation.isAck() || correlation.getReturned() != null) {
                throw new IllegalStateException("Analysis event was not routed and confirmed");
            }
            jdbc.update("""
                    UPDATE cicd.analysis_event_outbox SET published_at = CURRENT_TIMESTAMP,
                        lease_until = NULL, last_error = NULL WHERE event_id = ?
                    """, event.id());
        } catch (Exception exception) {
            if (exception instanceof InterruptedException) Thread.currentThread().interrupt();
            jdbc.update("""
                    UPDATE cicd.analysis_event_outbox SET attempts = attempts + 1,
                        next_attempt_at = CURRENT_TIMESTAMP + INTERVAL '30 seconds',
                        lease_until = NULL, last_error = ? WHERE event_id = ? AND published_at IS NULL
                    """, exception.getClass().getSimpleName(), event.id());
        }
    }

    private record Pending(UUID id, String payload, String routingKey) { }
}
