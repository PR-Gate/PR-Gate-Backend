package com.prgate.metrics.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prgate.metrics.application.dto.AnalysisCompletedEvent;
import com.prgate.metrics.application.port.out.AnalysisSnapshotStore;
import com.prgate.metrics.application.service.ReceiveAnalysisService;
import com.prgate.metrics.infrastructure.inbound.messaging.AnalysisEventListener;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReceiveAnalysisTest {
    private final AnalysisSnapshotStore store = mock(AnalysisSnapshotStore.class);
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
    private final AnalysisEventListener listener = new AnalysisEventListener(json, new ReceiveAnalysisService(store));

    @Test void failedQualityGateIsStillACompletedAnalysisAndMissingMeasuresStayMissing() throws Exception {
        var event = event("ERROR", Map.of("coverage", new BigDecimal("42")));
        listener.receive(message(json.writeValueAsBytes(event)));
        verify(store).saveIfAbsent(event);
        assertFalse(event.measures().containsKey("vulnerabilities"));
    }

    @Test void databaseFailurePropagatesSoDeliveryIsNotAcknowledgedAsProcessed() throws Exception {
        var event = event("OK", Map.of());
        when(store.saveIfAbsent(event)).thenThrow(new IllegalStateException("Database unavailable"));
        assertThrows(IllegalStateException.class,
                () -> listener.receive(message(json.writeValueAsBytes(event))));
    }

    @Test void identicalRedeliveryDoesNotFail() throws Exception {
        var event = event("OK", Map.of());
        when(store.saveIfAbsent(event)).thenReturn(false);
        assertDoesNotThrow(() -> listener.receive(message(json.writeValueAsBytes(event))));
    }

    @Test void invalidVersionAndMissingRevisionNeverReachPersistence() throws Exception {
        var document = json.valueToTree(event("OK", Map.of()));
        ((com.fasterxml.jackson.databind.node.ObjectNode) document).put("schemaVersion", 2);
        assertThrows(IllegalArgumentException.class,
                () -> listener.receive(message(json.writeValueAsBytes(document))));
        ((com.fasterxml.jackson.databind.node.ObjectNode) document).put("schemaVersion", 1).remove("commitSha");
        assertThrows(IllegalArgumentException.class,
                () -> listener.receive(message(json.writeValueAsBytes(document))));
        verifyNoInteractions(store);
    }

    @Test void malformedAndOversizedMessagesNeverReachPersistence() {
        assertThrows(IllegalArgumentException.class, () -> listener.receive(message(new byte[]{'{'})));
        assertThrows(IllegalArgumentException.class, () -> listener.receive(message(new byte[262145])));
        verifyNoInteractions(store);
    }

    private static Message message(byte[] bytes) { return new Message(bytes, new MessageProperties()); }

    private static AnalysisCompletedEvent event(String gate, Map<String, BigDecimal> measures) {
        return new AnalysisCompletedEvent(1, AnalysisCompletedEvent.TYPE, UUID.randomUUID(), Instant.now(),
                123, "example/backend", "refs/heads/main", "a".repeat(40), 456, 1,
                "example-backend", "task-123", "analysis-123", Instant.now(), gate, measures);
    }
}
