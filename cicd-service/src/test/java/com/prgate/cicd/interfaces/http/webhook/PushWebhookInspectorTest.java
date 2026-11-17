package com.prgate.cicd.interfaces.http.webhook;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prgate.cicd.application.port.out.PushReceiptPort;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PushWebhookInspectorTest {
    private final PushReceiptPort receipts = mock(PushReceiptPort.class);
    private final PushWebhookInspector inspector = new PushWebhookInspector(receipts);
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void recordsPushWithoutAcademicProjectOrWorkflow() throws Exception {
        UUID delivery = UUID.randomUUID();
        String before = "a".repeat(40), after = "b".repeat(40);
        when(receipts.record(delivery,123,"PRGATE-TEST-SQU","frontend","refs/heads/main",before,after,false,1)).thenReturn(true);
        var payload = json.readTree("""
                {"repository":{"id":123,"owner":{"login":"PRGATE-TEST-SQU"},"name":"frontend"},
                 "ref":"refs/heads/main","before":"%s","after":"%s","deleted":false,"commits":[{"id":"%s"}]}
                """.formatted(before,after,after));
        var result = inspector.inspect(delivery.toString(),payload);
        assertEquals("PUSH_RECEIVED",result.result());
        assertEquals("PRGATE-TEST-SQU/frontend",result.repository());
        assertFalse(result.analysisStarted());
        verify(receipts).record(delivery,123,"PRGATE-TEST-SQU","frontend","refs/heads/main",before,after,false,1);
        assertEquals("DUPLICATE_PUSH",new PushWebhookInspector((a,b,c,d,e,f,g,h,i)->false)
                .inspect(delivery.toString(),payload).result());
    }

    @Test
    void malformedPayloadIsNotStored() throws Exception {
        var payload = json.readTree("{\"repository\":{}}");
        assertThrows(IllegalArgumentException.class, () -> inspector.inspect(UUID.randomUUID().toString(),payload));
        verifyNoInteractions(receipts);
    }
}
