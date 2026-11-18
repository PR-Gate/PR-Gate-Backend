package com.prgate.metrics.infrastructure.inbound.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prgate.metrics.application.dto.AnalysisCompletedEvent;
import com.prgate.metrics.application.port.in.ReceiveAnalysisUseCase;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class AnalysisEventListener {
    private final ObjectMapper json;
    private final ReceiveAnalysisUseCase service;

    public AnalysisEventListener(ObjectMapper json, ReceiveAnalysisUseCase service) {
        this.json = json;
        this.service = service;
    }

    @RabbitListener(queues = AnalysisMessagingConfiguration.QUEUE)
    public void receive(Message message) {
        if (message.getBody().length > 262144) {
            throw new IllegalArgumentException("Analysis event exceeds 256 KiB");
        }
        final AnalysisCompletedEvent event;
        try { event = json.readValue(message.getBody(), AnalysisCompletedEvent.class); }
        catch (IOException exception) {
            // Do not attach a parser exception containing remote payload fragments to logs.
            throw new IllegalArgumentException("Invalid analysis event JSON");
        }
        service.receive(event);
    }
}
