package com.prgate.ai.domain;

import com.prgate.ai.domain.exception.DomainRuleViolation;
import com.prgate.ai.domain.model.aggregates.*;
import com.prgate.ai.domain.model.enums.*;
import com.prgate.ai.domain.model.valueobjects.*;
import com.prgate.ai.domain.policy.TechnicalCheckCatalog;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class DomainModelTest {
    private final CriterionId criterion = CriterionId.create();
    private final SubmissionReference submission = new SubmissionReference("student-1", "delivery-1", "commit-abc");

    private TechnicalAssessmentPlan plan(boolean approve) {
        var plan = new TechnicalAssessmentPlan(PlanId.create(), new RubricReference("rubric-1", 1),
            List.of(new RubricCriterion(criterion, "Readable code", "Explain maintainability")),
            List.of(new TechnicalCheck(criterion, CheckMechanism.SONAR_RULE, "java:example", Map.of(), "Readability")));
        if (approve) plan.approve(catalog(true));
        return plan;
    }
    private TechnicalCheckCatalog catalog(boolean supported) {
        return new TechnicalCheckCatalog() {
            public String version() { return "fixture-v1"; }
            public boolean supports(TechnicalCheck check) { return supported; }
        };
    }
    private Assessment analyzed() {
        var assessment = new Assessment(AssessmentId.create(), submission, plan(true));
        assessment.startAnalysis();
        assessment.completeAnalysis("analysis-1", submission, List.of(
            new TechnicalFinding(FindingId.create(), criterion, "java:example", "Main.java", 3, "Use a meaningful name")));
        return assessment;
    }
    private LearningQuestion question(Set<FindingId> evidence) {
        return new LearningQuestion(QuestionId.create(), criterion, "How would you rename this variable?",
            "A descriptive name", "Must express intent", evidence);
    }

    @Test void rejectsInvalidValueObjectsAndUsesValueEquality() {
        assertThrows(DomainRuleViolation.class, () -> new RubricReference("rubric", 0));
        assertThrows(DomainRuleViolation.class, () -> new SubmissionReference(" ", "delivery", "revision"));
        var id = UUID.randomUUID();
        assertEquals(new CriterionId(id), new CriterionId(id));
    }
    @Test void rejectsUncoveredAndDuplicateCriteria() {
        var c = new RubricCriterion(criterion, "Readability", "Improve naming");
        assertThrows(DomainRuleViolation.class, () -> new TechnicalAssessmentPlan(
            PlanId.create(), new RubricReference("r", 1), List.of(c), List.of()));
        assertThrows(DomainRuleViolation.class, () -> new TechnicalAssessmentPlan(
            PlanId.create(), new RubricReference("r", 1), List.of(c, c), List.of()));
    }
    @Test void unsupportedCatalogDoesNotPartiallyApprovePlan() {
        var plan = plan(false);
        assertThrows(DomainRuleViolation.class, () -> plan.approve(catalog(false)));
        assertEquals(PlanStatus.DRAFT, plan.status());
        assertTrue(plan.catalogVersion().isEmpty());
        plan.approve(catalog(true));
        assertEquals(PlanStatus.APPROVED, plan.status());
        assertThrows(DomainRuleViolation.class, () -> plan.approve(catalog(true)));
    }
    @Test void analysisRequiresApprovedPlanAndMatchingRevision() {
        assertThrows(DomainRuleViolation.class, () -> new Assessment(AssessmentId.create(), submission, plan(false)));
        var assessment = new Assessment(AssessmentId.create(), submission, plan(true));
        assertThrows(DomainRuleViolation.class, () -> assessment.completeFeedback("summary", List.of()));
        assessment.startAnalysis();
        assertThrows(DomainRuleViolation.class, () -> assessment.completeAnalysis("analysis", 
            new SubmissionReference("student-1", "delivery-1", "other-commit"), List.of()));
        assertEquals(AssessmentStatus.ANALYZING, assessment.status());
        assertTrue(assessment.analysisId().isEmpty());
    }
    @Test void fabricatedFeedbackIsRejectedAndRetryPreservesAnalysis() {
        var assessment = analyzed();
        assertThrows(DomainRuleViolation.class, () -> assessment.completeFeedback("summary",
            List.of(new FeedbackItem(criterion, Set.of(FindingId.create()), "Explain", "Practice"))));
        assessment.fail("provider unavailable");
        assertEquals(AssessmentStatus.FAILED, assessment.status());
        assessment.retry();
        assertEquals(AssessmentStatus.ANALYZED, assessment.status());
        assertEquals("analysis-1", assessment.analysisId().orElseThrow());
        assertEquals(1, assessment.findings().size());
        assessment.completeFeedback("Naming needs improvement", List.of(
            new FeedbackItem(criterion, Set.of(assessment.findings().getFirst().id()), "Unclear intent", "Rename")));
        assertEquals(AssessmentStatus.COMPLETED, assessment.status());
    }
    @Test void retryAfterAnalysisFailureReturnsToPendingAndDoesNotFakeSuccess() {
        var assessment = new Assessment(AssessmentId.create(), submission, plan(true));
        assessment.startAnalysis();
        assessment.fail("scanner failed");
        assertTrue(assessment.analysisId().isEmpty());
        assertThrows(DomainRuleViolation.class, () -> assessment.completeFeedback("ok", List.of()));
        assessment.retry();
        assertEquals(AssessmentStatus.PENDING, assessment.status());
    }
    @Test void reinforcementRequiresCompletedFeedbackAndRealEvidence() {
        var assessment = analyzed();
        var q = question(Set.of(assessment.findings().getFirst().id()));
        assertThrows(DomainRuleViolation.class, () -> LearningTest.reinforcement(LearningTestId.create(), assessment, List.of(q)));
        assessment.completeFeedback("Summary", List.of());
        assertThrows(DomainRuleViolation.class, () -> LearningTest.reinforcement(
            LearningTestId.create(), assessment, List.of(question(Set.of(FindingId.create())))));
        var test = LearningTest.reinforcement(LearningTestId.create(), assessment, List.of(q));
        assertEquals(submission.studentId(), test.studentId());
        assertEquals(assessment.id(), test.assessmentId().orElseThrow());
        test.publish();
        assertEquals(TestStatus.PUBLISHED, test.status());
        assertThrows(DomainRuleViolation.class, test::publish);
    }
    @Test void initialTestHasNoFindingsAndCollectionsAreImmutable() {
        var test = LearningTest.initial(LearningTestId.create(), "student-1", plan(true), List.of(question(Set.of())));
        assertTrue(test.assessmentId().isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> test.questions().clear());
        var source = new HashMap<String,String>();
        source.put("threshold", "10");
        var check = new TechnicalCheck(criterion, CheckMechanism.SONAR_METRIC, "complexity", source, "Threshold");
        source.put("threshold", "99");
        assertEquals("10", check.parameters().get("threshold"));
        assertThrows(UnsupportedOperationException.class, () -> check.parameters().clear());
    }
}