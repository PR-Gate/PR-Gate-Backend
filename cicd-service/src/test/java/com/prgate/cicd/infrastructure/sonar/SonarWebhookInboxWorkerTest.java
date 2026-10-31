package com.prgate.cicd.infrastructure.sonar;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prgate.cicd.application.model.RecordSonarAnalysisCommand;
import com.prgate.cicd.application.port.in.RecordSonarAnalysisUseCase;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SonarWebhookInboxWorkerTest {
    @Test
    void usesGitBranchMetadataAndAcceptsSonarMainLabelAndNumericTimezone() throws Exception {
        var json = new ObjectMapper();
        var sonar = mock(SonarProjectHttpAdapter.class);
        when(sonar.resolveAnalysisId("task-1", "prgate:test")).thenReturn("analysis-1");
        var worker = new SonarWebhookInboxWorker(mock(JdbcTemplate.class), json, sonar,
                mock(RecordSonarAnalysisUseCase.class));
        var payload = json.readTree("""
                {
                  "taskId":"task-1", "analysedAt":"2026-10-31T00:26:37-0500",
                  "revision":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                  "project":{"key":"prgate:test","url":"http://localhost:9000/dashboard?id=prgate:test"},
                  "branch":{"name":"main","isMain":true},
                  "qualityGate":{"status":"OK","conditions":[]},
                  "properties":{
                    "sonar.analysis.prgateRepositoryId":"123",
                    "sonar.analysis.prgateRunId":"456",
                    "sonar.analysis.prgateRunAttempt":"2",
                    "sonar.analysis.prgateBranch":"develop"
                  }
                }
                """);
        RecordSonarAnalysisCommand command = ReflectionTestUtils.invokeMethod(worker, "toCommand", payload);
        assertNotNull(command);
        assertEquals("develop", command.branch());
        assertEquals("2026-10-31T05:26:37Z", command.analysis().analyzedAt().toString());
        assertEquals("analysis-1", command.analysis().analysisId());
        assertNull(command.analysis().metrics().coveragePercent());
    }
}
