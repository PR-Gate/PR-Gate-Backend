package com.prgate.users.infraestructure.identity;

import com.prgate.users.domain.model.*;
import com.prgate.users.infraestructure.persistence.jpa.repositories.UserRepository;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.*;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.transaction.*;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class KeycloakIdentityServiceTest {
    UserRepository users;
    JdbcTemplate jdbc;
    JwtDecoder decoder;
    KeycloakIdentityService service;
    Jwt jwt;
    @BeforeEach void setup() {
        users = mock(UserRepository.class); jdbc = mock(JdbcTemplate.class); decoder = mock(JwtDecoder.class);
        var transactions = mock(PlatformTransactionManager.class);
        when(transactions.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        service = new KeycloakIdentityService(decoder, users, jdbc, transactions);
        jwt = Jwt.withTokenValue("fixture").header("alg", "RS256").issuer("http://localhost:8090/realms/prgate")
                .subject(UUID.randomUUID().toString()).issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300))
                .claim("given_name", "Ana").claim("family_name", "Torres").claim("email", "ana@example.test")
                .claim("realm_access", Map.of("roles", List.of("ADMIN"))).build();
        when(decoder.decode("fixture")).thenReturn(jwt);
    }
    @Test void provisionsStudentFromVerifiedClaimsWithoutReusingKeycloakIdOrEmail() {
        when(jdbc.query(anyString(), any(RowMapper.class), anyString(), anyString())).thenReturn(List.of());
        when(users.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        var user = service.registerProfile(request(), "student");
        assertNotEquals(jwt.getSubject(), user.getId().toString());
        assertEquals(UserRole.STUDENT, user.getRole());
        assertEquals(UserStatus.ACTIVE, user.getStatus());
        assertNull(user.getCognitoSub());
        verify(users, never()).findByCognitoSub(anyString());
        verify(jdbc).update(anyString(), eq(jwt.getIssuer().toString()), eq(jwt.getSubject()), eq(user.getId()), any());
    }
    @Test void resolvesExistingAcademicIdentityAndKeepsItsOfficialRole() {
        var user = User.registerExternal(UUID.randomUUID(), "Ana", "Torres", null, Instant.now());
        user.changeRole(UserRole.TEACHER, Instant.now().plusSeconds(1));
        when(jdbc.query(anyString(), any(RowMapper.class), anyString(), anyString())).thenReturn(List.of(user.getId()));
        when(users.findById(user.getId())).thenReturn(Optional.of(user));
        assertEquals(user.getId(), service.openSession(request()).getId());
        assertEquals(UserRole.TEACHER, service.current(request()).getRole());
        verify(users, never()).saveAndFlush(any());
    }
    @Test void registersTeacherOnlyWhenExplicitlySelectedAndNeverAcceptsAdmin() {
        when(jdbc.query(anyString(), any(RowMapper.class), anyString(), anyString())).thenReturn(List.of());
        when(users.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        assertEquals(UserRole.TEACHER, service.registerProfile(request(), "teacher").getRole());
        assertEquals(400, assertThrows(ResponseStatusException.class, () -> service.registerProfile(request(), "ADMIN")).getStatusCode().value());
    }
    @Test void normalLoginDoesNotCreateAProfileWithoutRegistrationChoice() {
        when(jdbc.query(anyString(), any(RowMapper.class), anyString(), anyString())).thenReturn(List.of());
        var missing = assertThrows(ResponseStatusException.class, () -> service.openSession(request()));
        assertEquals(404, missing.getStatusCode().value());
        assertEquals("ACADEMIC_PROFILE_REQUIRED", missing.getReason());
        verify(users, never()).saveAndFlush(any());
    }
    @Test void registrationCannotChangeAnExistingStudentIntoTeacher() {
        var student = User.registerExternal(UUID.randomUUID(), "Ana", "Torres", null, Instant.now());
        when(jdbc.query(anyString(), any(RowMapper.class), anyString(), anyString())).thenReturn(List.of(student.getId()));
        when(users.findById(student.getId())).thenReturn(Optional.of(student));
        assertEquals(UserRole.STUDENT, service.registerProfile(request(), "teacher").getRole());
        verify(users, never()).saveAndFlush(any());
    }
    @Test void rejectsInactiveUsersAndDoesNotCreateReplacementProfiles() {
        var user = User.registerExternal(UUID.randomUUID(), "Ana", "Torres", null, Instant.now());
        user.changeStatus(UserStatus.INACTIVE, Instant.now().plusSeconds(1));
        when(jdbc.query(anyString(), any(RowMapper.class), anyString(), anyString())).thenReturn(List.of(user.getId()));
        when(users.findById(user.getId())).thenReturn(Optional.of(user));
        assertEquals(403, assertThrows(ResponseStatusException.class, () -> service.openSession(request())).getStatusCode().value());
        verify(users, never()).saveAndFlush(any());
    }
    @Test void rejectsMissingAndInvalidBearerWithoutAccessingStorage() {
        assertEquals(401, assertThrows(ResponseStatusException.class,
                () -> service.openSession(new MockHttpServletRequest())).getStatusCode().value());
        when(decoder.decode("fixture")).thenThrow(new JwtException("invalid"));
        assertEquals(401, assertThrows(ResponseStatusException.class, () -> service.openSession(request())).getStatusCode().value());
        verifyNoInteractions(jdbc, users);
    }
    MockHttpServletRequest request() {
        var request = new MockHttpServletRequest(); request.addHeader("Authorization", "Bearer fixture"); return request;
    }
}
