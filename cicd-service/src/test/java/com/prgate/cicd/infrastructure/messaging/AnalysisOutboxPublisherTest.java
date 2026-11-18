package com.prgate.cicd.infrastructure.messaging;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AnalysisOutboxPublisherTest {
    @Test void confirmedPersistentMessageMarksOutboxPublished() throws Exception {
        check(false);
    }

    @Test void brokerAckWithoutDestinationDoesNotMarkPublished() throws Exception {
        check(true);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void check(boolean returned) throws Exception {
        var jdbc = mock(JdbcTemplate.class);
        var rabbit = mock(RabbitTemplate.class);
        var id = UUID.randomUUID();
        when(jdbc.query(anyString(), any(RowMapper.class))).thenAnswer(invocation -> {
            RowMapper mapper = invocation.getArgument(1);
            var row = mock(ResultSet.class);
            when(row.getObject(1, UUID.class)).thenReturn(id);
            when(row.getString(2)).thenReturn("{}");
            when(row.getString(3)).thenReturn("cicd.analysis.completed.v1");
            return List.of(mapper.mapRow(row, 0));
        });
        when(jdbc.update(contains("SET lease_until"), eq(id))).thenReturn(1);
        doAnswer(invocation -> {
            Message message = invocation.getArgument(2);
            assertEquals(id.toString(), message.getMessageProperties().getMessageId());
            assertEquals(org.springframework.amqp.core.MessageDeliveryMode.PERSISTENT,
                    message.getMessageProperties().getDeliveryMode());
            CorrelationData correlation = invocation.getArgument(3);
            if (returned) correlation.setReturned(new ReturnedMessage(
                    new Message(new byte[0], new MessageProperties()), 312,
                    "NO_ROUTE", "prgate.events", "cicd.analysis.completed.v1"));
            correlation.getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(rabbit).send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));
        new AnalysisOutboxPublisher(jdbc, rabbit).publishPending();
        if (returned) {
            verify(jdbc, never()).update(contains("SET published_at"), eq(id));
            verify(jdbc).update(contains("SET attempts = attempts + 1"), any(), eq(id));
        } else {
            verify(jdbc).update(contains("SET published_at"), eq(id));
        }
    }
}
