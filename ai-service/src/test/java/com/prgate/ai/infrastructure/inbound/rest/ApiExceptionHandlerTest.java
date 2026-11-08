package com.prgate.ai.infrastructure.inbound.rest;

import com.prgate.ai.application.port.out.RubricTranslationException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ApiExceptionHandlerTest {
    @Test void exposesSafeCreditDiagnosisWithUpstreamStatus() {
        var response = new ApiExceptionHandler().rubric(
                RubricTranslationException.fromProvider(429, "credit_balance_exhausted"));
        assertEquals(502, response.getStatus());
        assertEquals("OPENAI_CREDITS_EXHAUSTED", response.getProperties().get("code"));
        assertEquals(429, response.getProperties().get("upstreamStatus"));
        assertTrue(response.getDetail().contains("creditos"));
    }

    @Test void doesNotExposeLegacyExceptionMessages() {
        var response = new ApiExceptionHandler().rubric(new RubricTranslationException("private-provider-value"));
        assertEquals("AI_GENERATION_FAILED", response.getProperties().get("code"));
        assertFalse(response.getDetail().contains("private-provider-value"));
    }
}