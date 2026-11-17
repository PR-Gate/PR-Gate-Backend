package com.prgate.cicd.interfaces.http.webhook;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prgate.cicd.application.port.out.SonarWebhookInboxPort;
import com.prgate.cicd.application.port.out.SonarWebhookReceiptPort;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class SonarWebhookControllerTest {
    private final SonarWebhookInboxPort inbox = mock(SonarWebhookInboxPort.class);
    private final SonarWebhookReceiptPort receipts = mock(SonarWebhookReceiptPort.class);

    @Test
    void signedUncorrelatedAnalysisHasDurableReceiptButNoPipelineResult() throws Exception {
        String body = "{\"status\":\"SUCCESS\",\"taskId\":\"task-1\",\"project\":{\"key\":\"python-demo\"}}";
        when(receipts.record(any(),eq("python-demo"),eq("task-1"),eq("SUCCESS"))).thenReturn(true);
        var mvc = MockMvcBuilders.standaloneSetup(new SonarWebhookController(inbox,new ObjectMapper(),receipts))
                .addFilters(new GitHubWebhookSignatureFilter("github-fixture","sonar-fixture")).build();
        mvc.perform(post("/api/v1/webhooks/sonar").servletPath("/api/v1/webhooks/sonar")
                        .contentType("application/json").content(body)
                        .header("X-Sonar-Webhook-HMAC-SHA256",signature(body)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.result").value("SONAR_RECEIVED_UNCORRELATED"))
                .andExpect(jsonPath("$.duplicate").value(false));
        verify(receipts).record(any(),eq("python-demo"),eq("task-1"),eq("SUCCESS"));
        verifyNoInteractions(inbox);
    }

    @Test
    void invalidSignatureCannotCreateReceipt() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new SonarWebhookController(inbox,new ObjectMapper(),receipts))
                .addFilters(new GitHubWebhookSignatureFilter("github-fixture","sonar-fixture")).build();
        mvc.perform(post("/api/v1/webhooks/sonar").servletPath("/api/v1/webhooks/sonar")
                        .contentType("application/json").content("{}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(receipts,inbox);
    }

    @Test
    void correlatedAnalysisIsQueuedForExistingVerificationWorker() throws Exception {
        byte[] body = "{\"status\":\"SUCCESS\",\"taskId\":\"task-1\",\"project\":{\"key\":\"repo\"},\"properties\":{\"sonar.analysis.prgateRunId\":\"12\"}}"
                .getBytes(StandardCharsets.UTF_8);
        new SonarWebhookController(inbox,new ObjectMapper(),receipts).receive(body);
        verify(inbox).enqueue(body);
    }

    private String signature(String body) throws Exception {
        var mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec("sonar-fixture".getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
    }
}
