package com.prgate.cicd.infrastructure.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prgate.cicd.infrastructure.sonar.SonarProjectHttpAdapter;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.amqp.core.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named="CICD_TEST_DATABASE_URL",matches=".+")
class QualityGateWorkerDatabaseTest {
    @Test void lateOlderPoliciesCannotOverrideNewestAndFailuresProduceDurableResults() throws Exception {
        var source=new DriverManagerDataSource(System.getenv("CICD_TEST_DATABASE_URL"),"prgate_test","test");
        Flyway.configure().dataSource(source).schemas("cicd").defaultSchema("cicd").load().migrate();
        var jdbc=new JdbcTemplate(source);
        var sonar=mock(SonarProjectHttpAdapter.class);
        var json=new ObjectMapper().findAndRegisterModules();
        var worker=new QualityGateWorker(jdbc,json,sonar,new DataSourceTransactionManager(source));
        UUID project=UUID.randomUUID();
        long repository=java.util.concurrent.ThreadLocalRandom.current().nextLong(1000,Long.MAX_VALUE);
        String projectKey="example-backend-"+repository;
        jdbc.update("""
                INSERT INTO cicd.evaluation_repositories(id,academic_project_id,github_repository_id,
                    repository_owner,repository_name,sonar_project_key,analysis_branch,provisioning_status,registered_at,updated_at)
                VALUES (?,?,?,'example','backend',?,'main','READY',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                """,UUID.randomUUID(),project,repository,projectKey);
        var latest=request(project,repository,2);
        var older=request(project,repository,1);
        var message=message(json,latest);
        worker.receive(message); worker.receive(message); worker.receive(message(json,older));
        when(sonar.configureQualityGate(eq(latest.configurationId()),eq(projectKey),anyList())).thenReturn("prgate-"+latest.configurationId());
        worker.applyPending();
        assertEquals("APPLIED",jdbc.queryForObject("SELECT state FROM cicd.quality_gate_requests WHERE event_id=?",String.class,latest.eventId()));
        assertEquals("SUPERSEDED",jdbc.queryForObject("SELECT state FROM cicd.quality_gate_requests WHERE event_id=?",String.class,older.eventId()));
        verify(sonar,times(1)).configureQualityGate(any(),anyString(),anyList());
        assertEquals(2,jdbc.queryForObject("SELECT count(*) FROM cicd.analysis_event_outbox WHERE payload::jsonb->>'requestId' IN (?,?)",Integer.class,latest.eventId().toString(),older.eventId().toString()));
        var failed=request(project,repository,3);
        worker.receive(message(json,failed));
        jdbc.update("UPDATE cicd.quality_gate_requests SET attempts=19 WHERE event_id=?",failed.eventId());
        when(sonar.configureQualityGate(eq(failed.configurationId()),anyString(),anyList())).thenThrow(new IllegalStateException("unavailable"));
        worker.applyPending();
        assertEquals("FAILED",jdbc.queryForObject("SELECT state FROM cicd.quality_gate_requests WHERE event_id=?",String.class,failed.eventId()));
        assertEquals(3,jdbc.queryForObject("SELECT count(*) FROM cicd.analysis_event_outbox WHERE payload::jsonb->>'requestId' IN (?,?,?)",Integer.class,latest.eventId().toString(),older.eventId().toString(),failed.eventId().toString()));
    }
    private QualityGateRequest request(UUID project,long repository,long version) {
        return new QualityGateRequest(1,"metrics.quality-gate.requested.v1",UUID.randomUUID(),UUID.randomUUID(),project,
                repository,"example","refs/heads/main",version,Instant.now(),List.of(new QualityGateRequest.Condition("coverage","LT",java.math.BigDecimal.valueOf(80))));
    }
    private Message message(ObjectMapper json,QualityGateRequest request) throws Exception {
        return MessageBuilder.withBody(json.writeValueAsBytes(request)).build();
    }
}
