package com.prgate.ai.infrastructure.outbound.gemini;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prgate.ai.application.dto.*;
import com.prgate.ai.domain.model.valueobjects.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.web.client.RestClient;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Opt-in real-provider smoke test. No Spring context, repositories or database. */
@EnabledIfEnvironmentVariable(named = "GEMINI_LIVE_TEST", matches = "true")
class GeminiLiveAdapterTest {
    @Test void generatesRubricAndEvidenceBackedFeedbackWithRealGemini() {
        var model = System.getenv().getOrDefault("GEMINI_MODEL", "gemini-3.8-flash");
        var client = new GeminiClient(RestClient.builder(), new ObjectMapper(),
                System.getenv("GEMINI_API_KEY"), model, "https://generativelanguage.googleapis.com/v1beta");
        var criterion = CriterionId.create();
        var criteria = List.of(new RubricCriterion(criterion,
                "Explicar verbalmente las decisiones de diseno", "Justificar la solucion"));
        var proposal = new GeminiRubricAdapter(client).translate(new RubricTranslationRequest(
                new RubricReference("synthetic-live-test", 1), new SourceLanguage("java"), criteria));
        assertFalse(proposal.checks().isEmpty());
        assertTrue(proposal.checks().stream().allMatch(c -> c.criterionId().equals(criterion)));
        var finding = FindingId.create();
        var result = new GeminiFeedbackAdapter(client).generate(new FeedbackRequest(criteria,
                List.of(new TechnicalFinding(finding, criterion, "synthetic:naming", "Example.java", 1,
                        "La variable x no expresa el proposito de su contenido"))));
        assertFalse(result.summary().isBlank());
        assertFalse(result.feedback().isEmpty());
        assertTrue(result.feedback().stream().allMatch(f ->
                f.criterionId().equals(criterion) && f.evidenceIds().equals(Set.of(finding))));
    }
}