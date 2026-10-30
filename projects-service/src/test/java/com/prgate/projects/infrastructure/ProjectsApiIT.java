package com.prgate.projects.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.prgate.projects.application.exception.UserDirectoryException;
import com.prgate.projects.application.model.UserAccount;
import com.prgate.projects.application.port.out.GitHubOrganizationDirectory;
import com.prgate.projects.domain.model.valueobject.GitHubOrganization;
import com.prgate.projects.infrastructure.users.LocalUserDirectory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Testcontainers
class ProjectsApiIT {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username",POSTGRES::getUsername);
        registry.add("spring.datasource.password",POSTGRES::getPassword);
    }
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @MockitoBean private LocalUserDirectory users;
    @MockitoBean private GitHubOrganizationDirectory github;
    private UUID teacher, student, outsider;
    @BeforeEach void setup() {
        teacher=UUID.randomUUID(); student=UUID.randomUUID(); outsider=UUID.randomUUID();
        user(teacher,UserAccount.Role.TEACHER);
        user(student,UserAccount.Role.STUDENT);
        user(outsider,UserAccount.Role.STUDENT);
    }
    private void user(UUID id, UserAccount.Role role) {
        when(users.findById(id)).thenReturn(Optional.of(new UserAccount(id,role,UserAccount.Status.ACTIVE)));
    }
    private JsonNode postJson(String path, UUID actor, Object body, int status) throws Exception {
        var request=post(path).header("X-User-Id",actor).contentType(MediaType.APPLICATION_JSON);
        if (body!=null) { request.content(json.writeValueAsBytes(body)); }
        var result=mvc.perform(request).andExpect(status().is(status)).andReturn();
        return json.readTree(result.getResponse().getContentAsString());
    }
    private String course() throws Exception {
        return postJson("/api/v1/courses",teacher,Map.of("name","Software","section","01","academicPeriod","2026-2"),201).get("id").asText();
    }
    private String group(String course) throws Exception {
        return postJson("/api/v1/courses/"+course+"/projects",teacher,Map.of("name","Group"),201).get("id").asText();
    }
    @Test void completeAcademicFlowThroughHttp() throws Exception {
        String course=course(), first=group(course), second=group(course);
        String enrollment=postJson("/api/v1/courses/"+course+"/enrollments",student,null,201).get("id").asText();
        mvc.perform(get("/api/v1/courses/"+course+"/enrollments").header("X-User-Id",teacher))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].status").value("PENDING"));
        mvc.perform(patch("/api/v1/enrollments/"+enrollment).header("X-User-Id",teacher).contentType(MediaType.APPLICATION_JSON).content("{\"decision\":\"APPROVE\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("APPROVED"));
        postJson("/api/v1/projects/"+first+"/memberships",student,null,201);
        mvc.perform(put("/api/v1/projects/"+first+"/leader").header("X-User-Id",teacher).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(Map.of("studentId",student))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.leaderId").value(student.toString()));
        when(github.findByLogin("prgate-team")).thenReturn(Optional.of(new GitHubOrganization(42,"prgate-team")));
        postJson("/api/v1/projects/"+first+"/organization",student,Map.of("organizationLogin","prgate-team"),201);
        postJson("/api/v1/projects/"+first+"/transfers",teacher,Map.of("targetProjectId",second,"studentId",student),201);
        mvc.perform(get("/api/v1/projects/"+first).header("X-User-Id",teacher)).andExpect(status().isOk())
                .andExpect(jsonPath("$.leaderId").isEmpty()).andExpect(jsonPath("$.organizationId").value(42));
        mvc.perform(get("/api/v1/projects/"+first+"/members").header("X-User-Id",teacher)).andExpect(jsonPath("$.items").isEmpty());
        mvc.perform(get("/api/v1/projects/"+second+"/members").header("X-User-Id",student)).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].studentId").value(student.toString()));
        mvc.perform(get("/api/v1/courses/"+course+"/students").header("X-User-Id",teacher)).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].status").value("APPROVED"));
        mvc.perform(get("/api/v1/me/enrollments").header("X-User-Id",student)).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(enrollment));
    }
    @Test void unauthenticatedAndInvalidLocalIdentityAre401() throws Exception {
        mvc.perform(get("/api/v1/courses")).andExpect(status().isUnauthorized()).andExpect(content().contentTypeCompatibleWith("application/problem+json"));
        mvc.perform(get("/api/v1/courses").header("X-User-Id","bad")).andExpect(status().isUnauthorized());
    }
    @Test void unknownInactiveAndUnavailableUsersAreNotAuthorized() throws Exception {
        mvc.perform(get("/api/v1/courses").header("X-User-Id",UUID.randomUUID())).andExpect(status().isNotFound());
        when(users.findById(student)).thenReturn(Optional.of(new UserAccount(student,UserAccount.Role.STUDENT,UserAccount.Status.INACTIVE)));
        mvc.perform(get("/api/v1/courses").header("X-User-Id",student)).andExpect(status().isForbidden());
        when(users.findById(teacher)).thenThrow(new UserDirectoryException("connection failed"));
        mvc.perform(get("/api/v1/courses").header("X-User-Id",teacher)).andExpect(status().isServiceUnavailable());
    }
    @Test void preventsCrossCourseReadsAndAdmissionDataDisclosure() throws Exception {
        String course=course(), group=group(course);
        mvc.perform(get("/api/v1/courses/"+course).header("X-User-Id",outsider)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/projects/"+group+"/members").header("X-User-Id",outsider)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/courses/"+course+"/enrollments").header("X-User-Id",student)).andExpect(status().isForbidden());
        UUID anotherTeacher=UUID.randomUUID(); user(anotherTeacher,UserAccount.Role.TEACHER);
        mvc.perform(get("/api/v1/courses/"+course).header("X-User-Id",anotherTeacher)).andExpect(status().isForbidden());
    }
    @Test void validatesBodiesAndRejectsForgedActorFields() throws Exception {
        postJson("/api/v1/courses",teacher,Map.of("name"," ","section","01","academicPeriod","2026-2"),400);
        postJson("/api/v1/courses",teacher,Map.of("name","Software","section","01","academicPeriod","2026-2","teacherId",outsider),400);
        mvc.perform(get("/api/v1/courses").header("X-User-Id",teacher).param("size","101")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/courses/not-a-uuid").header("X-User-Id",teacher)).andExpect(status().isBadRequest());
    }
    @Test void rejectedStudentCanRequestAgainAndCannotSelfApprove() throws Exception {
        String course=course();
        String first=postJson("/api/v1/courses/"+course+"/enrollments",student,null,201).get("id").asText();
        mvc.perform(patch("/api/v1/enrollments/"+first).header("X-User-Id",student).contentType(MediaType.APPLICATION_JSON).content("{\"decision\":\"APPROVE\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/api/v1/enrollments/"+first).header("X-User-Id",teacher).contentType(MediaType.APPLICATION_JSON).content("{\"decision\":\"REJECT\"}"))
                .andExpect(status().isOk());
        String second=postJson("/api/v1/courses/"+course+"/enrollments",student,null,201).get("id").asText();
        assertThat(second).isNotEqualTo(first);
        postJson("/api/v1/courses/"+course+"/enrollments",student,null,409);
    }
    @Test void listsAreScopedAndPaginated() throws Exception {
        String first=course(); course();
        mvc.perform(get("/api/v1/courses").header("X-User-Id",teacher).param("size","1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(1)).andExpect(jsonPath("$.hasNext").value(true));
        mvc.perform(get("/api/v1/courses").header("X-User-Id",student)).andExpect(jsonPath("$.items").isEmpty());
        mvc.perform(get("/api/v1/courses/catalog").header("X-User-Id",student)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/courses/"+first+"/projects").header("X-User-Id",teacher)).andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty());
    }
    @Test void servesOpenApiSwaggerAndHealth() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/courses']").exists())
                .andExpect(jsonPath("$.components.securitySchemes.localUser.name").value("X-User-Id"));
        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
    }

    @Test void disconnectedDirectoryReturns503WhileHealthRemainsAvailable() throws Exception {
        when(users.findById(teacher)).thenCallRealMethod();
        mvc.perform(get("/api/v1/courses").header("X-User-Id",teacher))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }
}
