package com.prgate.users.identity;

import com.prgate.users.domain.model.*;
import com.prgate.users.infraestructure.identity.KeycloakIdentityService;
import com.prgate.users.interfaces.rest.AccountSessionController;
import org.junit.jupiter.api.*;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.util.UUID;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AccountRegistrationContractTest {
    KeycloakIdentityService identities;
    MockMvc mvc;
    @BeforeEach void setup() {
        identities = mock(KeycloakIdentityService.class);
        mvc = MockMvcBuilders.standaloneSetup(new AccountSessionController(identities)).build();
    }
    @Test void missingProfileHasADistinctCodeFromAMissingEndpoint() throws Exception {
        when(identities.openSession(any())).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "ACADEMIC_PROFILE_REQUIRED"));
        mvc.perform(post("/api/me/session")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ACADEMIC_PROFILE_REQUIRED"));
    }
    @Test void explicitRegistrationReturnsTheConfirmedProfile() throws Exception {
        var teacher = User.registerExternal(UUID.randomUUID(), "Ana", "Torres", null, Instant.now());
        teacher.changeRole(UserRole.TEACHER, Instant.now().plusSeconds(1));
        when(identities.registerProfile(any(), eq("teacher"))).thenReturn(teacher);
        mvc.perform(post("/api/me/registration").contentType("application/json").content("{\"profile\":\"teacher\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.role").value("TEACHER"))
                .andExpect(header().string("Cache-Control", "no-store"));
    }
    @Test void unauthenticatedRegistrationRemainsUnauthorized() throws Exception {
        when(identities.registerProfile(any(), anyString())).thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required"));
        mvc.perform(post("/api/me/registration").contentType("application/json").content("{\"profile\":\"student\"}"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }
}
