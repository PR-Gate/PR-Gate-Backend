package com.prgate.projects.infrastructure.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.prgate.projects.application.model.UserAccount;
import com.prgate.projects.application.port.in.CreateCourseUseCase;
import com.prgate.projects.application.port.in.CreateProjectUseCase;
import com.prgate.projects.application.port.in.RequestCourseEnrollmentUseCase;
import com.prgate.projects.application.port.in.ProjectQueries;
import com.prgate.projects.application.port.out.CognitoIdentityDirectory;
import com.prgate.projects.application.result.PageResult;
import com.prgate.projects.application.security.Actor;
import com.prgate.projects.interfaces.rest.controller.CourseController;
import com.prgate.projects.interfaces.rest.security.ActorResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers=CourseController.class, properties={
        "prgate.security.issuer=https://cognito-idp.us-east-1.amazonaws.com/test", "prgate.security.client-id=app-client"})
@Import({CognitoSecurityConfiguration.class,SecurityProblemHandler.class,ActorResolver.class,CognitoSecurityTest.Keys.class})
class CognitoSecurityTest {
    private static final String ISSUER="https://cognito-idp.us-east-1.amazonaws.com/test";
    private static final RSAKey KEY=key();
    private final UUID localId=UUID.randomUUID();
    @Autowired private MockMvc mvc;
    @MockitoBean private CreateCourseUseCase createCourse;
    @MockitoBean private CreateProjectUseCase createProject;
    @MockitoBean private RequestCourseEnrollmentUseCase enroll;
    @MockitoBean private ProjectQueries queries;
    @MockitoBean private CognitoIdentityDirectory identities;
    private static RSAKey key() {
        try { return new RSAKeyGenerator(2048).keyID(UUID.randomUUID().toString()).generate(); }
        catch (JOSEException e) { throw new IllegalStateException(e); }
    }
    @TestConfiguration
    static class Keys {
        @Bean @Primary
        JwtDecoder signedTestDecoder() throws JOSEException {
            var decoder=NimbusJwtDecoder.withPublicKey(KEY.toRSAPublicKey()).build();
            decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer(ISSUER),new CognitoTokenValidator("app-client")));
            return decoder;
        }
    }
    @BeforeEach void setup() {
        when(identities.findByCognitoSub("subject-1")).thenReturn(Optional.of(new UserAccount(localId,UserAccount.Role.STUDENT,UserAccount.Status.ACTIVE)));
        when(queries.courses(any(),any())).thenReturn(new PageResult<>(List.of(),0,20,false));
    }
    private String token(String issuer,String client,String use,Instant expiry,RSAKey key) throws JOSEException {
        var claims=new JWTClaimsSet.Builder().issuer(issuer).subject("subject-1").issueTime(Date.from(Instant.now()))
                .claim("client_id",client).claim("token_use",use);
        if (expiry!=null) { claims.expirationTime(Date.from(expiry)); }
        var jwt=new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(),claims.build());
        jwt.sign(new RSASSASigner(key));
        return jwt.serialize();
    }
    @Test void signedAccessTokenResolvesSubjectInsteadOfTrustingUuidClaims() throws Exception {
        String token=token(ISSUER,"app-client","access",Instant.now().plusSeconds(300),KEY);
        mvc.perform(get("/api/v1/courses").header("Authorization","Bearer "+token)).andExpect(status().isOk());
        verify(queries).courses(eq(new Actor(localId)),any());
    }
    @Test void defaultProfileDoesNotAcceptLocalIdentityHeader() throws Exception {
        mvc.perform(get("/api/v1/courses").header("X-User-Id",localId)).andExpect(status().isUnauthorized());
    }
    @Test void rejectsIdTokensWrongClientAndWrongIssuer() throws Exception {
        for (String token : List.of(token(ISSUER,"app-client","id",Instant.now().plusSeconds(300),KEY),
                token(ISSUER,"other","access",Instant.now().plusSeconds(300),KEY),
                token("https://other.example","app-client","access",Instant.now().plusSeconds(300),KEY))) {
            mvc.perform(get("/api/v1/courses").header("Authorization","Bearer "+token)).andExpect(status().isUnauthorized());
        }
    }
    @Test void rejectsExpiredMissingExpiryAndInvalidSignature() throws Exception {
        for (String token : List.of(token(ISSUER,"app-client","access",Instant.now().minusSeconds(120),KEY),
                token(ISSUER,"app-client","access",null,KEY),
                token(ISSUER,"app-client","access",Instant.now().plusSeconds(300),key()))) {
            mvc.perform(get("/api/v1/courses").header("Authorization","Bearer "+token)).andExpect(status().isUnauthorized());
        }
    }
    @Test void registeredLocalAccountIsRequired() throws Exception {
        when(identities.findByCognitoSub("subject-1")).thenReturn(Optional.empty());
        mvc.perform(get("/api/v1/courses").header("Authorization","Bearer "+token(ISSUER,"app-client","access",Instant.now().plusSeconds(300),KEY)))
                .andExpect(status().isForbidden());
    }
}
