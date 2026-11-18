package com.prgate.metrics.infrastructure.inbound.messaging;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AnalysisMessagingConfiguration {
    public static final String EXCHANGE = "prgate.events";
    public static final String ROUTING_KEY = "cicd.analysis.completed.v1";
    public static final String QUEUE = "metrics.analysis.completed.v1";
    public static final String DEAD_EXCHANGE = "prgate.metrics.dead";
    public static final String DEAD_QUEUE = "metrics.analysis.completed.v1.dead";

    @Bean
    Declarables analysisTopology() {
        var events = new TopicExchange(EXCHANGE, true, false);
        var dead = new DirectExchange(DEAD_EXCHANGE, true, false);
        var queue = QueueBuilder.durable(QUEUE)
                .deadLetterExchange(DEAD_EXCHANGE).deadLetterRoutingKey(DEAD_QUEUE).build();
        var deadQueue = QueueBuilder.durable(DEAD_QUEUE).build();
        return new Declarables(events, dead, queue, deadQueue,
                BindingBuilder.bind(queue).to(events).with(ROUTING_KEY),
                BindingBuilder.bind(deadQueue).to(dead).with(DEAD_QUEUE));
    }
}
