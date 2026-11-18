package com.prgate.metrics.integration;

import com.fasterxml.jackson.databind.*;
import com.prgate.metrics.application.dto.AnalysisCompletedEvent;
import com.prgate.metrics.application.port.in.ReceiveAnalysisUseCase;
import com.prgate.metrics.application.port.out.*;
import com.prgate.metrics.infrastructure.inbound.messaging.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named="METRICS_TEST_DATABASE_URL",matches=".+")
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
        "spring.datasource.url=${METRICS_TEST_DATABASE_URL}","spring.datasource.username=prgate_test",
        "spring.datasource.password=test","spring.rabbitmq.username=test","spring.rabbitmq.password=test",
        "spring.rabbitmq.listener.simple.auto-startup=false","spring.rabbitmq.dynamic=false","app.api.token=test-api"})
class RubricApiDatabaseTest {
    @Autowired TestRestTemplate http;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired ReceiveAnalysisUseCase receiver;
    @MockBean ProjectAccessPort access;
    @MockBean SonarEvidencePort sonar;
    @MockBean EvaluationWorker worker;
    @MockBean QualityGateMessaging messaging;
    @MockBean GradeEstimatorPort estimator;

    @Test void persistsTwentyPointRubricDeduplicatesEvaluationsAndEnforcesProjectPermissions() throws Exception {
        UUID actor=UUID.randomUUID(), student=UUID.randomUUID(), outsider=UUID.randomUUID(), project=UUID.randomUUID();
        long repository=java.util.concurrent.ThreadLocalRandom.current().nextLong(1000,Long.MAX_VALUE);
        when(access.check(eq(project),any())).thenAnswer(invocation->{
            UUID who=invocation.getArgument(1);
            return new ProjectAccessPort.Access(who.equals(actor)||who.equals(student),who.equals(actor),"example");
        });
        String criterionOne=UUID.randomUUID().toString(),criterionTwo=UUID.randomUUID().toString();
        var rubric=call(project,actor,"/rubrics",HttpMethod.POST,"""
                {"title":"Twenty point rubric","criteria":[
                  {"id":"%s","description":"Tests","objective":"Coverage","maxPoints":12},
                  {"id":"%s","description":"Review","objective":"Explanation","maxPoints":8}]}
                """.formatted(criterionOne,criterionTwo));
        assertEquals(HttpStatus.CREATED,rubric.getStatusCode());
        assertEquals(20,rubric.getBody().path("maxPoints").asInt());
        String rubricId=rubric.getBody().path("id").asText();
        String manual=UUID.randomUUID().toString();
        var configured=call(project,actor,"/configurations",HttpMethod.POST,"""
                {"rubricId":"%s","rubricVersion":1,"repositoryId":%d,"gitRef":"refs/heads/main",
                 "planId":"%s","checks":[
                    {"criterionId":"%s","type":"METRIC","key":"coverage","operator":"GTE","threshold":80},
                    {"id":"%s","criterionId":"%s","type":"MANUAL","key":"Explain the code"}]}
                """.formatted(rubricId,repository,UUID.randomUUID(),criterionOne,manual,criterionTwo));
        assertEquals(HttpStatus.CREATED,configured.getStatusCode(),configured.toString());
        String configuration=configured.getBody().path("id").asText();
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM metrics.quality_gate_outbox WHERE configuration_id=?",Integer.class,UUID.fromString(configuration)));
        var gate=call(project,student,"/configurations/"+configuration+"/quality-gate",HttpMethod.GET,null);
        assertEquals("PENDING",gate.getBody().path("synchronization_state").asText());
        assertEquals(HttpStatus.FORBIDDEN,call(project,student,"/configurations/"+configuration+"/quality-gate/retry",HttpMethod.POST,null).getStatusCode());
        assertEquals(HttpStatus.OK,call(project,actor,"/configurations/"+configuration+"/quality-gate/retry",HttpMethod.POST,null).getStatusCode());
        assertEquals(2,jdbc.queryForObject("SELECT count(*) FROM metrics.quality_gate_outbox WHERE configuration_id=?",Integer.class,UUID.fromString(configuration)));
        UUID eventId=UUID.randomUUID();
        var event=new AnalysisCompletedEvent(1,AnalysisCompletedEvent.TYPE,eventId,Instant.now(),repository,"example/backend",
                "refs/heads/main","a".repeat(40),456,1,"example-backend","task","analysis",Instant.now(),"OK",Map.of());
        receiver.receive(event);
        jdbc.update("UPDATE metrics.analysis_evidence SET state='COLLECTED',measures=CAST(? AS jsonb) WHERE event_id=?","{\"coverage\":90}",eventId);
        String command="{\"configurationId\":\""+configuration+"\",\"eventId\":\""+eventId+"\"}";
        var first=call(project,actor,"/evaluations",HttpMethod.POST,command);
        assertEquals(HttpStatus.OK,first.getStatusCode(),first.toString());
        assertEquals("PARTIAL",first.getBody().path("status").asText());
        assertTrue(first.getBody().path("technicalCompliance").path("completeCompliancePercent").isNull());
        String evaluationId=first.getBody().path("id").asText();
        assertEquals(evaluationId,call(project,actor,"/evaluations",HttpMethod.POST,command).getBody().path("id").asText());
        assertEquals(HttpStatus.FORBIDDEN,call(project,student,"/evaluations",HttpMethod.POST,command).getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN,call(project,outsider,"/evaluations/"+evaluationId,HttpMethod.GET,null).getStatusCode());
        assertEquals(HttpStatus.OK,call(project,student,"/evaluations/"+evaluationId,HttpMethod.GET,null).getStatusCode());
        var reviewed=call(project,actor,"/evaluations/"+evaluationId+"/manual-reviews",HttpMethod.POST,
                "{\"checkId\":\""+manual+"\",\"passed\":true,\"justification\":\"Teacher verified explanation\"}");
        assertEquals(HttpStatus.OK,reviewed.getStatusCode(),reviewed.toString());
        assertEquals("COMPLETED",reviewed.getBody().path("status").asText());
        assertEquals(100,reviewed.getBody().path("technicalCompliance").path("completeCompliancePercent").asInt());
        assertEquals(HttpStatus.BAD_REQUEST,call(project,actor,"/evaluations/"+evaluationId+"/manual-reviews",HttpMethod.POST,
                "{\"checkId\":\""+manual+"\",\"passed\":true,\"justification\":\"Duplicate\"}").getStatusCode());
        var evidence=call(project,student,"/evaluations/"+evaluationId+"/analysis",HttpMethod.GET,null);
        assertEquals(90,evidence.getBody().path("evidence").path("measures").path("coverage").asInt());
        var missingCredential=http.getForEntity("/internal/v1/projects/"+project+"/evaluations?repositoryId=123",String.class);
        assertEquals(HttpStatus.UNAUTHORIZED,missingCredential.getStatusCode());
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM metrics.rubric_evaluations WHERE event_id=?",Integer.class,eventId));
        when(estimator.estimate(any())).thenAnswer(invocation->{
            com.prgate.metrics.application.dto.GradeEstimationEvidence proof=invocation.getArgument(0);
            assertEquals(0,proof.criteria().getFirst().maxPoints().compareTo(java.math.BigDecimal.valueOf(12)));
            return "{\"id\":\""+UUID.randomUUID()+"\",\"evaluationId\":\""+evaluationId+"\",\"officialGrade\":false,\"estimatedScore\":18,\"maxScore\":20}";
        });
        assertEquals(HttpStatus.FORBIDDEN,call(project,student,"/evaluations/"+evaluationId+"/grade-estimate",HttpMethod.POST,null).getStatusCode());
        var estimate=call(project,actor,"/evaluations/"+evaluationId+"/grade-estimate",HttpMethod.POST,null);
        assertEquals(HttpStatus.OK,estimate.getStatusCode(),estimate.toString());
        assertFalse(estimate.getBody().path("officialGrade").asBoolean());
        assertEquals(18,call(project,student,"/evaluations/"+evaluationId+"/grade-estimate",HttpMethod.GET,null).getBody().path("estimatedScore").asInt());
    }
    private ResponseEntity<JsonNode> call(UUID project,UUID actor,String path,HttpMethod method,String body) throws Exception {
        var headers=new HttpHeaders(); headers.setBearerAuth("test-api"); headers.set("X-Actor-Id",actor.toString());
        headers.setContentType(MediaType.APPLICATION_JSON);
        return http.exchange("/internal/v1/projects/"+project+path,method,
                new HttpEntity<>(body==null?null:json.readTree(body),headers),JsonNode.class);
    }
}
