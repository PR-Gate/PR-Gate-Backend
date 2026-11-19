package com.prgate.metrics.integration;

import com.fasterxml.jackson.databind.*;
import com.prgate.metrics.application.dto.AnalysisCompletedEvent;
import com.prgate.metrics.application.port.in.ReceiveAnalysisUseCase;
import com.prgate.metrics.application.port.out.*;
import com.prgate.metrics.infrastructure.inbound.messaging.*;
import com.prgate.metrics.infrastructure.outbound.ai.AiGradeEstimatorHttpAdapter;
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
        "spring.rabbitmq.listener.simple.auto-startup=false","spring.rabbitmq.dynamic=false","app.api.token=test-api",
        "app.evaluation.estimate-delay-ms=3600000"})
class RubricApiDatabaseTest {
    @Autowired TestRestTemplate http;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired ReceiveAnalysisUseCase receiver;
    @Autowired GradeEstimateWorker gradeWorker;
    @MockBean ProjectAccessPort access;
    @MockBean SonarEvidencePort sonar;
    @MockBean EvaluationWorker worker;
    @MockBean QualityGateMessaging messaging;
    @MockBean AiGradeEstimatorHttpAdapter estimator;

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
        assertEquals(HttpStatus.NOT_FOUND,call(project,student,"/evaluations/"+evaluationId+"/grade-estimate",HttpMethod.GET,null).getStatusCode());
        gradeWorker.processNext();
        assertEquals(18,call(project,student,"/evaluations/"+evaluationId+"/grade-estimate",HttpMethod.GET,null).getBody().path("estimatedScore").asInt());
        var estimate=call(project,actor,"/evaluations/"+evaluationId+"/grade-estimate",HttpMethod.POST,null);
        assertEquals(HttpStatus.OK,estimate.getStatusCode(),estimate.toString());
        assertFalse(estimate.getBody().path("officialGrade").asBoolean());
        assertEquals(18,call(project,student,"/evaluations/"+evaluationId+"/grade-estimate",HttpMethod.GET,null).getBody().path("estimatedScore").asInt());
        verify(estimator,times(1)).estimate(any());

        UUID practiceEvent = UUID.randomUUID();
        receiver.receive(new AnalysisCompletedEvent(1, AnalysisCompletedEvent.TYPE, practiceEvent, Instant.now(),
                repository, "example/backend", "refs/heads/main", "b".repeat(40), 457, 1,
                "example-backend", "practice-task", "practice-analysis", Instant.now(), "ERROR", Map.of()));
        jdbc.update("UPDATE metrics.analysis_evidence SET state='COLLECTED',measures=CAST(? AS jsonb) WHERE event_id=?",
                "{\"coverage\":20}", practiceEvent);
        assertEquals(HttpStatus.OK, call(project, actor, "/evaluations", HttpMethod.POST,
                "{\"configurationId\":\"" + configuration + "\",\"eventId\":\"" + practiceEvent + "\"}").getStatusCode());
        when(estimator.practiceQuestions(anyList())).thenReturn(json.readTree("""
                [{"prompt":"Pregunta 1","options":["a","b","c","d"],"correctOption":0,"explanation":"Detalle 1"},
                 {"prompt":"Pregunta 2","options":["a","b","c","d"],"correctOption":1,"explanation":"Detalle 2"},
                 {"prompt":"Pregunta 3","options":["a","b","c","d"],"correctOption":2,"explanation":"Detalle 3"}]
                """));
        var practice = call(project, student, "/practice", HttpMethod.POST, "{}");
        assertEquals(HttpStatus.OK, practice.getStatusCode(), practice.toString());
        assertTrue(practice.getBody().path("questions").get(0).path("correctOption").isNull());
        String submit = "/practice/" + practice.getBody().path("id").asText() + "/submit";
        assertEquals(HttpStatus.NOT_FOUND, call(project, actor, submit, HttpMethod.POST,
                "{\"answers\":[0,1,0]}").getStatusCode());
        var completed = call(project, student, submit, HttpMethod.POST, "{\"answers\":[0,1,0]}");
        assertEquals(HttpStatus.OK, completed.getStatusCode(), completed.toString());
        assertEquals(2, completed.getBody().path("correctCount").asInt());
        assertEquals("Detalle 1", completed.getBody().path("questions").get(0).path("explanation").asText());
    }
    @Test void courseAllowsOneRubricAndCanArchiveItWithoutLosingVersions() throws Exception {
        UUID course = UUID.randomUUID(), teacher = UUID.randomUUID(), student = UUID.randomUUID();
        when(access.checkCourse(course, teacher)).thenReturn(new ProjectAccessPort.Access(true, true, null));
        when(access.checkCourse(course, student)).thenReturn(new ProjectAccessPort.Access(true, false, null));
        String path = "/internal/v1/courses/" + course + "/rubrics";
        String body = """
                {"title":"Code quality","criteria":[
                  {"id":"%s","description":"Duplication","objective":"Review duplication","maxPoints":20}]}
                """.formatted(UUID.randomUUID());
        var first = request(teacher, path, HttpMethod.POST, body);
        assertEquals(HttpStatus.CREATED, first.getStatusCode());
        String rubricId = first.getBody().path("id").asText();
        String versions = path + "/" + rubricId + "/versions";
        String archive = path + "/" + rubricId + "/archive";
        assertEquals(HttpStatus.CONFLICT, request(teacher, path, HttpMethod.POST, body).getStatusCode());
        assertEquals(2, request(teacher, versions, HttpMethod.POST, body).getBody().path("version").asInt());
        assertEquals(HttpStatus.FORBIDDEN, request(student, archive, HttpMethod.POST, null).getStatusCode());
        assertEquals(HttpStatus.NO_CONTENT, request(teacher, archive, HttpMethod.POST, null).getStatusCode());
        assertEquals(0, request(student, path, HttpMethod.GET, null).getBody().size());
        assertEquals(HttpStatus.NOT_FOUND, request(teacher, versions, HttpMethod.POST, body).getStatusCode());
        assertEquals(2, jdbc.queryForObject("""
                SELECT count(*) FROM metrics.published_rubrics WHERE rubric_id=? AND archived_at IS NOT NULL
                """, Integer.class, UUID.fromString(rubricId)));
        assertEquals(HttpStatus.CREATED, request(teacher, path, HttpMethod.POST, body).getStatusCode());
    }

    private ResponseEntity<JsonNode> call(UUID project,UUID actor,String path,HttpMethod method,String body) throws Exception {
        return request(actor, "/internal/v1/projects/" + project + path, method, body);
    }

    private ResponseEntity<JsonNode> request(UUID actor,String path,HttpMethod method,String body) throws Exception {
        var headers=new HttpHeaders(); headers.setBearerAuth("test-api"); headers.set("X-Actor-Id",actor.toString());
        headers.setContentType(MediaType.APPLICATION_JSON);
        return http.exchange(path,method,
                new HttpEntity<>(body==null?null:json.readTree(body),headers),JsonNode.class);
    }
}
