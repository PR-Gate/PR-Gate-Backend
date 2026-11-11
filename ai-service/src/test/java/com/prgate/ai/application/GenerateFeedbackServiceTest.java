package com.prgate.ai.application;

import com.prgate.ai.application.dto.*;
import com.prgate.ai.application.exception.*;
import com.prgate.ai.application.port.out.*;
import com.prgate.ai.application.service.GenerateFeedbackService;
import com.prgate.ai.domain.exception.DomainRuleViolation;
import com.prgate.ai.domain.model.aggregates.*;
import com.prgate.ai.domain.model.enums.*;
import com.prgate.ai.domain.model.valueobjects.*;
import com.prgate.ai.domain.policy.TechnicalCheckCatalog;
import com.prgate.ai.domain.repository.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class GenerateFeedbackServiceTest {
    private final AssessmentRepository assessments = mock(AssessmentRepository.class);
    private final TechnicalAssessmentPlanRepository plans = mock(TechnicalAssessmentPlanRepository.class);
    private final FeedbackGenerator generator = mock(FeedbackGenerator.class);
    private final GenerateFeedbackService service = new GenerateFeedbackService(assessments, plans, generator);
    private final CriterionId criterion = CriterionId.create();

    private TechnicalAssessmentPlan plan() {
        var plan = new TechnicalAssessmentPlan(PlanId.create(), new RubricReference("r", 1),
                new SourceLanguage("java"),
                List.of(new RubricCriterion(criterion, "Naming", "Communicate intent")),
                List.of(new TechnicalCheck(criterion, CheckMechanism.SONAR_RULE, "java:fixture", Map.of(), "Naming")));
        plan.approve(new TechnicalCheckCatalog() {
            public String version() { return "v1"; }
            public boolean supports(TechnicalCheck check) { return true; }
        });
        return plan;
    }

    private Assessment assessment(boolean analyze) {
        var plan = plan();
        var assessment = new Assessment(AssessmentId.create(), new SubmissionReference("s", "d", "rev"), plan);
        if (analyze) {
            assessment.startAnalysis();
            assessment.completeAnalysis("analysis", assessment.submission(), List.of(
                    new TechnicalFinding(FindingId.create(), criterion, "java:fixture", "Main.java", 1, "Unclear name")));
        }
        when(assessments.findById(assessment.id())).thenReturn(Optional.of(assessment));
        when(plans.findById(plan.id())).thenReturn(Optional.of(plan));
        return assessment;
    }

    @Test void generatesAndPersistsFeedbackWithExistingEvidence() {
        var assessment = assessment(true);
        var feedback = new FeedbackResult("Summary", List.of(new FeedbackItem(
                criterion, Set.of(assessment.findings().getFirst().id()), "Unclear name", "Rename it")));
        when(generator.generate(any())).thenReturn(feedback);
        var result = service.generate(assessment.id());
        assertEquals(assessment.id(), result.assessmentId());
        assertEquals(feedback, result.result());
        assertEquals(AssessmentStatus.COMPLETED, assessment.status());
        verify(generator).generate(argThat(r -> r.findings().equals(assessment.findings()) && r.criteria().size() == 1));
        verify(assessments).save(assessment);
    }

    @Test void missingAssessmentAndWrongStateNeverCallGenerator() {
        var missing = AssessmentId.create();
        when(assessments.findById(missing)).thenReturn(Optional.empty());
        assertThrows(AssessmentNotFoundException.class, () -> service.generate(missing));
        var pending = assessment(false);
        assertThrows(DomainRuleViolation.class, () -> service.generate(pending.id()));
        verifyNoInteractions(generator);
        verify(assessments, never()).save(any());
    }

    @Test void missingPlanDoesNotCallGeneratorOrChangeAssessment() {
        var assessment = assessment(true);
        when(plans.findById(assessment.planId())).thenReturn(Optional.empty());
        assertThrows(TechnicalPlanNotFoundException.class, () -> service.generate(assessment.id()));
        assertEquals(AssessmentStatus.ANALYZED, assessment.status());
        verifyNoInteractions(generator);
        verify(assessments, never()).save(any());
    }

    @Test void generationFailurePersistsSafeCategoryAndRetainsAnalysisForRetry() {
        var assessment = assessment(true);
        when(generator.generate(any())).thenThrow(new FeedbackGenerationException("Provider unavailable"));
        assertThrows(FeedbackGenerationException.class, () -> service.generate(assessment.id()));
        assertEquals(AssessmentStatus.FAILED, assessment.status());
        assertEquals("FEEDBACK_GENERATION_FAILED", assessment.failureReason().orElseThrow());
        assertEquals(AssessmentStatus.ANALYZED, assessment.failedAt().orElseThrow());
        verify(assessments).save(assessment);
        assessment.retry();
        assertEquals(AssessmentStatus.ANALYZED, assessment.status());
        assertEquals("analysis", assessment.analysisId().orElseThrow());
    }

    @Test void invalidProviderEvidenceDoesNotBecomeSuccessfulFeedback() {
        var assessment = assessment(true);
        when(generator.generate(any())).thenReturn(new FeedbackResult("Summary", List.of(
                new FeedbackItem(criterion, Set.of(FindingId.create()), "Invented", "Suggestion"))));
        assertThrows(FeedbackGenerationException.class, () -> service.generate(assessment.id()));
        assertEquals(AssessmentStatus.FAILED, assessment.status());
        assertTrue(assessment.feedback().isEmpty());
        verify(assessments).save(assessment);
    }

    @Test void completedAssessmentCannotGenerateAgain() {
        var assessment = assessment(true);
        assessment.completeFeedback("Existing", List.of());
        assertThrows(DomainRuleViolation.class, () -> service.generate(assessment.id()));
        verifyNoInteractions(generator);
    }
}