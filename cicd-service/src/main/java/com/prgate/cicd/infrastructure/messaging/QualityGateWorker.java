package com.prgate.cicd.infrastructure.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prgate.cicd.infrastructure.sonar.SonarProjectHttpAdapter;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.Instant;
import java.util.*;

@Component
@ConditionalOnProperty(name="app.messaging.enabled",havingValue="true")
public class QualityGateWorker {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final SonarProjectHttpAdapter sonar;
    private final TransactionTemplate transaction;
    public QualityGateWorker(JdbcTemplate jdbc, ObjectMapper json, SonarProjectHttpAdapter sonar,
                              PlatformTransactionManager manager) {
        this.jdbc=jdbc; this.json=json; this.sonar=sonar; this.transaction=new TransactionTemplate(manager);
    }
    @Bean
    Declarables qualityGateCommands() {
        var exchange=new TopicExchange("prgate.commands",true,false);
        var dead=new DirectExchange("prgate.cicd.dead",true,false);
        var queue=QueueBuilder.durable("cicd.quality-gate.requests.v1")
                .deadLetterExchange(dead.getName()).deadLetterRoutingKey("cicd.quality-gate.requests.v1.dead").build();
        var deadQueue=QueueBuilder.durable("cicd.quality-gate.requests.v1.dead").build();
        return new Declarables(exchange,dead,queue,deadQueue,
                BindingBuilder.bind(queue).to(exchange).with("metrics.quality-gate.requested.v1"),
                BindingBuilder.bind(deadQueue).to(dead).with(deadQueue.getName()));
    }
    @RabbitListener(queues="cicd.quality-gate.requests.v1")
    public void receive(Message message) {
        if (message.getBody().length>65536) throw new IllegalArgumentException("Quality Gate request too large");
        final QualityGateRequest value;
        try { value=json.readValue(message.getBody(),QualityGateRequest.class); }
        catch (java.io.IOException exception) { throw new IllegalArgumentException("Invalid Quality Gate request JSON"); }
        if (value==null) throw new IllegalArgumentException("Missing Quality Gate request");
        String payload=new String(message.getBody(),java.nio.charset.StandardCharsets.UTF_8);
        int inserted=jdbc.update("""
                INSERT INTO cicd.quality_gate_requests(event_id,repository_id,policy_version,payload)
                VALUES (?,?,?,?) ON CONFLICT (event_id) DO NOTHING
                """,value.eventId(),value.repositoryId(),value.policyVersion(),payload);
        if (inserted==0 && !Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT CAST(payload AS jsonb)=CAST(? AS jsonb) FROM cicd.quality_gate_requests WHERE event_id=?",
                Boolean.class,payload,value.eventId()))) throw new IllegalArgumentException("Quality Gate event identity reused");
    }
    @Scheduled(fixedDelayString="${app.messaging.gate-delay-ms:10000}")
    public void applyPending() {
        var rows=jdbc.query("""
                SELECT payload FROM cicd.quality_gate_requests WHERE state='PENDING'
                AND next_attempt_at<=CURRENT_TIMESTAMP ORDER BY policy_version DESC LIMIT 10
                """,(row,i)->row.getString(1));
        for (String payload:rows) {
            final QualityGateRequest request;
            try { request=json.readValue(payload,QualityGateRequest.class); }
            catch (java.io.IOException exception) { throw new IllegalStateException("Invalid stored Quality Gate request"); }
            try {
                transaction.executeWithoutResult(ignored->{
                    if (!Boolean.TRUE.equals(jdbc.queryForObject("SELECT pg_try_advisory_xact_lock(?)",Boolean.class,request.repositoryId()))) return;
                    String state=jdbc.queryForObject("SELECT state FROM cicd.quality_gate_requests WHERE event_id=?",String.class,request.eventId());
                    if (!"PENDING".equals(state)) return;
                    Long latest=jdbc.queryForObject("SELECT MAX(policy_version) FROM cicd.quality_gate_requests WHERE repository_id=?",Long.class,request.repositoryId());
                    if (latest!=null && latest>request.policyVersion()) { complete(request,"SUPERSEDED","",""); return; }
                    String key=target(request);
                    String gate=sonar.configureQualityGate(request.configurationId(),key,request.conditions());
                    complete(request,"APPLIED",gate,key);
                });
            } catch (Exception exception) {
                transaction.executeWithoutResult(ignored->{
                    if (!Boolean.TRUE.equals(jdbc.queryForObject("SELECT pg_try_advisory_xact_lock(?)",Boolean.class,request.repositoryId()))) return;
                    if (!"PENDING".equals(jdbc.queryForObject("SELECT state FROM cicd.quality_gate_requests WHERE event_id=?",String.class,request.eventId()))) return;
                    int attempts=jdbc.queryForObject("""
                            UPDATE cicd.quality_gate_requests SET attempts=attempts+1,
                            next_attempt_at=CURRENT_TIMESTAMP+INTERVAL '30 seconds',last_error=?
                            WHERE event_id=? RETURNING attempts
                            """,Integer.class,exception.getClass().getSimpleName(),request.eventId());
                    if (attempts>=20) complete(request,"FAILED","","");
                });
            }
        }
    }
    private String target(QualityGateRequest request) {
        var registered=jdbc.query("""
                SELECT academic_project_id, repository_owner, analysis_branch, sonar_project_key
                FROM cicd.evaluation_repositories WHERE github_repository_id=?
                """,(row,i)->new Target(row.getObject(1,UUID.class),row.getString(2),row.getString(3),row.getString(4)),request.repositoryId());
        if (!registered.isEmpty()) {
            var target=registered.getFirst();
            if (!target.project().equals(request.projectId()) || !target.owner().equalsIgnoreCase(request.organization())
                    || !request.gitRef().equals("refs/heads/"+target.branch())) throw new IllegalStateException("Repository policy ownership mismatch");
            return target.key();
        }
        // Technical pilot fallback: only independently received push+Sonar evidence, never a caller-supplied key.
        var keys=jdbc.query("""
                SELECT DISTINCT project_key FROM cicd.push_analysis_receipts
                WHERE repository_id=? AND lower(owner_login)=lower(?) AND git_ref=? AND analysis_status='SUCCESS'
                """,(row,i)->row.getString(1),request.repositoryId(),request.organization(),request.gitRef());
        if (keys.size()!=1) throw new IllegalStateException("Repository Sonar project is unavailable or ambiguous");
        return keys.getFirst();
    }
    private void complete(QualityGateRequest request,String state,String gate,String key) {
        jdbc.update("UPDATE cicd.quality_gate_requests SET state=? WHERE event_id=?",state,request.eventId());
        var result=json.createObjectNode();
        result.put("schemaVersion",1).put("eventType","cicd.quality-gate.configured.v1")
                .put("requestId",request.eventId().toString()).put("configurationId",request.configurationId().toString())
                .put("policyVersion",request.policyVersion()).put("status",state).put("gateName",gate)
                .put("sonarProjectKey",key).put("occurredAt",Instant.now().toString());
        UUID id=UUID.nameUUIDFromBytes((request.eventId()+":"+state).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        jdbc.update("""
                INSERT INTO cicd.analysis_event_outbox(event_id,payload,routing_key)
                VALUES (?,?,'cicd.quality-gate.configured.v1') ON CONFLICT (event_id) DO NOTHING
                """,id,result.toString());
    }
    private record Target(UUID project,String owner,String branch,String key) { }
}
