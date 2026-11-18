package com.prgate.metrics.infrastructure.inbound.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Component
public class QualityGateMessaging {
    private final JdbcTemplate jdbc;
    private final RabbitTemplate rabbit;
    private final ObjectMapper json;
    public QualityGateMessaging(JdbcTemplate jdbc, RabbitTemplate rabbit, ObjectMapper json) {
        this.jdbc = jdbc; this.rabbit = rabbit; this.json = json;
    }
    @Bean
    Declarables qualityGateResultTopology() {
        var events = new TopicExchange("prgate.events", true, false);
        var commands = new TopicExchange("prgate.commands", true, false);
        var queue = QueueBuilder.durable("metrics.quality-gate.results.v1")
                .deadLetterExchange(AnalysisMessagingConfiguration.DEAD_EXCHANGE)
                .deadLetterRoutingKey("metrics.quality-gate.results.v1.dead").build();
        var dead = QueueBuilder.durable("metrics.quality-gate.results.v1.dead").build();
        return new Declarables(events, commands, queue, dead,
                BindingBuilder.bind(queue).to(events).with("cicd.quality-gate.configured.v1"),
                BindingBuilder.bind(dead).to(new DirectExchange(AnalysisMessagingConfiguration.DEAD_EXCHANGE, true, false))
                        .with("metrics.quality-gate.results.v1.dead"));
    }
    @Scheduled(fixedDelayString="${app.messaging.publish-delay-ms:5000}")
    public void publishPending() {
        var rows = jdbc.query("""
                SELECT event_id, payload FROM metrics.quality_gate_outbox WHERE published_at IS NULL
                AND next_attempt_at<=CURRENT_TIMESTAMP AND (lease_until IS NULL OR lease_until<CURRENT_TIMESTAMP)
                ORDER BY policy_version LIMIT 10
                """, (row, i) -> new Pending(row.getObject(1, UUID.class), row.getString(2)));
        for (var event : rows) {
            int claimed = jdbc.update("""
                    UPDATE metrics.quality_gate_outbox SET lease_until=CURRENT_TIMESTAMP+INTERVAL '60 seconds'
                    WHERE event_id=? AND published_at IS NULL AND (lease_until IS NULL OR lease_until<CURRENT_TIMESTAMP)
                    """, event.id());
            if (claimed != 1) continue;
            try {
                var correlation = new CorrelationData(event.id().toString());
                rabbit.send("prgate.commands", "metrics.quality-gate.requested.v1",
                        MessageBuilder.withBody(event.payload().getBytes(StandardCharsets.UTF_8))
                                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                                .setDeliveryMode(MessageDeliveryMode.PERSISTENT).setMessageId(event.id().toString()).build(), correlation);
                if (!correlation.getFuture().get(10, TimeUnit.SECONDS).isAck() || correlation.getReturned()!=null)
                    throw new IllegalStateException("Quality Gate command not routed");
                jdbc.update("UPDATE metrics.quality_gate_outbox SET published_at=CURRENT_TIMESTAMP, lease_until=NULL, last_error=NULL WHERE event_id=?", event.id());
            } catch (Exception exception) {
                if (exception instanceof InterruptedException) Thread.currentThread().interrupt();
                jdbc.update("""
                        UPDATE metrics.quality_gate_outbox SET attempts=attempts+1, lease_until=NULL,
                        next_attempt_at=CURRENT_TIMESTAMP+INTERVAL '30 seconds', last_error=? WHERE event_id=?
                        """, exception.getClass().getSimpleName(), event.id());
            }
        }
    }
    @RabbitListener(queues="metrics.quality-gate.results.v1")
    public void result(Message message) {
        if (message.getBody().length > 65536) throw new IllegalArgumentException("Quality Gate result too large");
        final com.fasterxml.jackson.databind.JsonNode value;
        try { value = json.readTree(message.getBody()); }
        catch (java.io.IOException exception) { throw new IllegalArgumentException("Invalid Quality Gate result JSON"); }
        if (value == null || value.path("schemaVersion").asInt()!=1
                || !"cicd.quality-gate.configured.v1".equals(value.path("eventType").asText())
                || !java.util.Set.of("APPLIED", "FAILED", "SUPERSEDED").contains(value.path("status").asText()))
            throw new IllegalArgumentException("Invalid Quality Gate result");
        int changed = jdbc.update("""
                UPDATE metrics.quality_gate_outbox SET result_state=?, gate_name=?, sonar_project_key=?
                WHERE event_id=? AND configuration_id=? AND policy_version=?
                AND (result_state IS NULL OR result_state=?)
                """, value.path("status").asText(), value.path("gateName").asText(),
                value.path("sonarProjectKey").asText(), UUID.fromString(value.path("requestId").asText()),
                UUID.fromString(value.path("configurationId").asText()), value.path("policyVersion").asLong(),value.path("status").asText());
        if (changed!=1) throw new IllegalArgumentException("Unknown Quality Gate request result");
    }
    private record Pending(UUID id, String payload) { }
}
