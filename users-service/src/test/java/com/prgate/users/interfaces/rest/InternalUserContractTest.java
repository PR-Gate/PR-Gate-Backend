package com.prgate.users.interfaces.rest;

import com.prgate.users.application.internal.commandservices.UserCommandService;
import com.prgate.users.domain.model.UserRole;
import com.prgate.users.domain.model.UserStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "prgate.internal.service-token=" + InternalUserContractTest.SERVICE_TOKEN)
@AutoConfigureMockMvc
@org.springframework.test.context.ActiveProfiles("test")
class InternalUserContractTest {
    static final String SERVICE_TOKEN = "test-service-token-1234";
    private static final String AUTHORIZATION = "Bearer " + SERVICE_TOKEN;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserCommandService userCommandService;

    @Test
    void returnsActiveTeacherWithOnlyContractFields() throws Exception {
        UUID userId = registerUser("Ana", "Torres");
        userCommandService.changeUserRole(userId, UserRole.TEACHER, Instant.now().plusSeconds(1));

        mockMvc.perform(get("/internal/v1/users/{userId}", userId)
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(userId.toString()))
                .andExpect(jsonPath("$.firstName").value("Ana"))
                .andExpect(jsonPath("$.lastName").value("Torres"))
                .andExpect(jsonPath("$.role").value("TEACHER"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.cognitoSub").doesNotExist())
                .andExpect(jsonPath("$.contactEmail").doesNotExist());
    }

    @Test
    void returnsInactiveUserWith200() throws Exception {
        UUID userId = registerUser("Luis", "Paredes");
        userCommandService.changeUserStatus(userId, UserStatus.INACTIVE, Instant.now().plusSeconds(1));

        mockMvc.perform(get("/internal/v1/users/{userId}", userId)
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(userId.toString()))
                .andExpect(jsonPath("$.role").value("STUDENT"))
                .andExpect(jsonPath("$.status").value("INACTIVE"));
    }

    @Test
    void returns404WhenUserDoesNotExist() throws Exception {
        mockMvc.perform(get("/internal/v1/users/{userId}", UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void returns400WhenUserIdIsMalformed() throws Exception {
        mockMvc.perform(get("/internal/v1/users/{userId}", "not-a-uuid")
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void returns401WhenCredentialsAreMissing() throws Exception {
        mockMvc.perform(get("/internal/v1/users/{userId}", UUID.randomUUID()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void returns403WhenServiceIsNotAuthorized() throws Exception {
        mockMvc.perform(get("/internal/v1/users/{userId}", UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer wrong-service-token"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    private UUID registerUser(String firstName, String lastName) {
        UUID userId = UUID.randomUUID();
        userCommandService.registerUser(
                userId,
                "cognito-" + userId,
                firstName,
                lastName,
                null,
                Instant.now());
        return userId;
    }
}
