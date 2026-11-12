package com.prgate.metrics.domain;

import com.prgate.metrics.domain.exception.DomainRuleViolation;
import com.prgate.metrics.domain.model.aggregates.*;
import com.prgate.metrics.domain.model.entities.RubricCriterion;
import com.prgate.metrics.domain.model.enums.*;
import com.prgate.metrics.domain.model.valueobjects.*;
import com.prgate.metrics.domain.policy.CompliancePolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.prgate.metrics.domain.model.enums.CheckStatus.*;

class MetricsDomainTest {
    private static final Instant NOW = Instant.parse("2026-11-10T12:00:00Z");
    private static final String SHA = "a".repeat(40);
    private final CompliancePolicy policy = new CompliancePolicy();

    @Test void publicationRequiresExactlyOneHundredWeightAndIsAtomic() {
        var rubric = draft(criterion("90"));
        assertThrows(DomainRuleViolation.class, rubric::publish);
        assertEquals(RubricStatus.DRAFT, rubric.status());
        rubric.revise("Corrected", List.of(criterion("100")));
        assertEquals("Corrected", rubric.publish().title());
    }

    @Test void publishedVersionCannotBeEditedAndNewVersionDoesNotChangeHistory() {
        var originalCriterion = criterion("100");
        var rubric = draft(originalCriterion);
        var snapshot = rubric.publish();
        assertThrows(DomainRuleViolation.class, () -> rubric.revise("Other", List.of()));
        var next = rubric.nextVersion();
        assertEquals(rubric.id(), next.id());
        assertEquals(2, next.version().value());
        next.revise("Version 2", List.of(criterion("100")));
        next.publish();
        assertEquals(List.of(originalCriterion), snapshot.criteria());
        assertEquals(1, snapshot.version().value());
        assertEquals(RubricStatus.PUBLISHED, rubric.status());
    }

    @Test void rubricRejectsEmptyPublicationDuplicateIdsAndMutableCollections() {
        assertThrows(DomainRuleViolation.class, () -> draft().publish());
        var c = criterion("50");
        assertThrows(DomainRuleViolation.class, () -> draft(c, c));
        var input = new ArrayList<>(List.of(criterion("100")));
        var rubric = new Rubric(RubricId.create(), new RubricVersion(1), "Rubric", input);
        input.clear();
        assertEquals(1, rubric.criteria().size());
        assertThrows(UnsupportedOperationException.class, () -> rubric.criteria().clear());
    }

    @Test void approvalRequiresExactRubricAndCatalogAndCanHappenOnlyOnce() {
        var rubric = draft(criterion("100")).publish();
        var check = rule(rubric.criteria().get(0));
        var configuration = configuration(rubric, List.of(check));
        assertThrows(DomainRuleViolation.class, () -> configuration.approve(teacher(), Optional.empty()));
        assertTrue(configuration.approved().isEmpty());
        var planId = UUID.randomUUID();
        var wrong = new CatalogValidation(planId, "catalog-v1", Set.of(check.id()), NOW);
        assertThrows(DomainRuleViolation.class, () -> configuration.approve(teacher(), Optional.of(wrong)));
        var approved = approve(rubric, List.of(check));
        assertEquals(rubric, approved.rubric());
        assertThrows(UnsupportedOperationException.class, () -> approved.checks().clear());
        var manual = new RubricEvaluationConfiguration(ConfigurationId.create(), rubric,
                new TechnicalPlanReference(UUID.randomUUID(), rubric.id(), rubric.version()),
                List.of(new EvaluationCheck(CheckId.create(), rubric.criteria().get(0).id(), new ManualCheck("Review"))));
        manual.approve(teacher(), Optional.empty());
        assertThrows(DomainRuleViolation.class, () -> manual.approve(teacher(), Optional.empty()));
    }

    @Test void invalidCheckCoverageAndWrongRubricVersionCannotBeApproved() {
        var rubric = draft(criterion("100")).publish();
        assertThrows(DomainRuleViolation.class, () -> approve(rubric, List.of()));
        var unknown = new EvaluationCheck(CheckId.create(), CriterionId.create(), new ManualCheck("Review"));
        assertThrows(DomainRuleViolation.class, () -> approve(rubric, List.of(unknown)));
        var wrongPlan = new TechnicalPlanReference(UUID.randomUUID(), rubric.id(), new RubricVersion(2));
        var config = new RubricEvaluationConfiguration(ConfigurationId.create(), rubric, wrongPlan,
                List.of(rule(rubric.criteria().get(0))));
        assertThrows(DomainRuleViolation.class, () -> config.approve(teacher(), Optional.empty()));
    }

    @Test void approvalRejectsIncompleteCatalogAndFutureValidation() {
        var rubric = draft(criterion("100")).publish();
        var check = rule(rubric.criteria().get(0));
        var plan = new TechnicalPlanReference(UUID.randomUUID(), rubric.id(), rubric.version());
        var configuration = new RubricEvaluationConfiguration(ConfigurationId.create(), rubric, plan, List.of(check));
        assertThrows(DomainRuleViolation.class, () -> configuration.approve(teacher(),
                Optional.of(new CatalogValidation(plan.planId(), "v1", Set.of(), NOW))));
        assertThrows(DomainRuleViolation.class, () -> configuration.approve(teacher(),
                Optional.of(new CatalogValidation(plan.planId(), "v1", Set.of(check.id()), NOW.plusSeconds(1)))));
    }

    @Test void noIssuesPassOnlyWhenRuleExecutionAndCompleteScopeAreVerified() {
        var fixture = fixture();
        for (boolean executed : List.of(false, true)) {
            for (boolean complete : List.of(false, true)) {
                var observation = new RuleObservation(fixture.check.id(), "java:S1481", executed, complete, List.of());
                var result = policy.evaluate(fixture.config, snapshot(List.of(observation)), List.of());
                assertEquals(executed && complete ? PASSED : NOT_EVALUABLE, result.criteria().get(0).status());
                assertEquals(executed && complete, result.finalPercentage().isPresent());
            }
        }
    }

    @Test void realIssuesFailAndRetainEvidenceForFeedback() {
        var fixture = fixture();
        var issue = new IssueEvidence("issue-1", "java:S1481", "Example.java", OptionalInt.of(12), "Unused variable");
        var observation = new RuleObservation(fixture.check.id(), "java:S1481", true, true, List.of(issue));
        var evaluation = evaluation(fixture.config);
        evaluation.start();
        evaluation.complete(snapshot(List.of(observation)), List.of(), policy);
        assertEquals(EvaluationStatus.COMPLETED, evaluation.status());
        assertEquals(FAILED, evaluation.summary().orElseThrow().criteria().get(0).status());
        assertEquals(new BigDecimal("0.00"), evaluation.summary().orElseThrow().finalPercentage().orElseThrow().value());
        var saved = (RuleObservation) evaluation.analysis().orElseThrow().observations().get(0);
        assertEquals(issue, saved.issues().get(0));
    }

    @Test void missingObservationsAreNotAnAutomaticPass() {
        var fixture = fixture();
        var result = policy.evaluate(fixture.config, snapshot(List.of()), List.of());
        assertEquals(NOT_EVALUABLE, result.criteria().get(0).status());
        assertEquals(new BigDecimal("0.00"), result.coverage().value());
        assertTrue(result.provisionalPercentage().isEmpty());
        assertTrue(result.finalPercentage().isEmpty());
    }

    @Test void weightedScoreUsesCriterionWeightsRatherThanNumberOfChecks() {
        var first = criterion("70"); var second = criterion("30");
        var rubric = draft(first, second).publish();
        var a = rule(first); var b = rule(second);
        var config = approve(rubric, List.of(a, b));
        var failedIssue = new IssueEvidence("issue", "java:S1481", "A.java", OptionalInt.empty(), "Unused");
        var observations = List.<CheckObservation>of(
                new RuleObservation(a.id(), "java:S1481", true, true, List.of()),
                new RuleObservation(b.id(), "java:S1481", true, true, List.of(failedIssue)));
        var result = policy.evaluate(config, snapshot(observations), List.of());
        assertEquals(new BigDecimal("70.00"), result.finalPercentage().orElseThrow().value());
        assertEquals(new BigDecimal("100.00"), result.coverage().value());
    }

    @Test void partialScoreCannotBeMistakenForFinalAndManualReviewCompletesIt() {
        var first = criterion("60"); var second = criterion("40");
        var rubric = draft(first, second).publish();
        var automatic = rule(first);
        var manual = new EvaluationCheck(CheckId.create(), second.id(), new ManualCheck("Explain design"));
        var evaluation = evaluation(approve(rubric, List.of(automatic, manual)));
        evaluation.start();
        evaluation.complete(snapshot(List.of(new RuleObservation(automatic.id(), "java:S1481", true, true, List.of()))),
                List.of(), policy);
        var summary = evaluation.summary().orElseThrow();
        assertEquals(EvaluationStatus.PARTIAL, evaluation.status());
        assertEquals(new BigDecimal("60.00"), summary.coverage().value());
        assertEquals(new BigDecimal("100.00"), summary.provisionalPercentage().orElseThrow().value());
        assertTrue(summary.finalPercentage().isEmpty());
        evaluation.recordManualReview(new ManualDecision(manual.id(), false, UUID.randomUUID(), NOW, "Missing justification"), policy);
        assertEquals(EvaluationStatus.COMPLETED, evaluation.status());
        assertEquals(new BigDecimal("60.00"), evaluation.summary().orElseThrow().finalPercentage().orElseThrow().value());
        assertEquals(1, evaluation.decisions().size());
    }

    @Test void allMandatoryChecksMustPassAndUnknownEvidencePreventsFinalScore() {
        var fixture = fixture();
        var manual = new EvaluationCheck(CheckId.create(), fixture.check.criterionId(), new ManualCheck("Review"));
        var config = approve(fixture.config.rubric(), List.of(fixture.check, manual));
        var result = policy.evaluate(config, snapshot(List.of(new RuleObservation(fixture.check.id(),
                "java:S1481", true, true, List.of()))), List.of());
        assertEquals(NOT_EVALUABLE, result.criteria().get(0).status());
        assertTrue(result.finalPercentage().isEmpty());
    }

    @ParameterizedTest
    @CsvSource({"GTE,80,80,true", "GTE,79.9,80,false", "LTE,3,3,true", "LTE,4,3,false",
            "GT,80,80,false", "LT,80,80,false", "EQ,80.00,80,true"})
    void metricThresholdsAreDeterministic(String operator, String value, String threshold, boolean expected) {
        var rubric = draft(criterion("100")).publish();
        var spec = new MetricCheck("coverage", ComparisonOperator.valueOf(operator), new BigDecimal(threshold));
        var check = new EvaluationCheck(CheckId.create(), rubric.criteria().get(0).id(), spec);
        var result = policy.evaluate(approve(rubric, List.of(check)), snapshot(List.of(new MetricObservation(
                check.id(), "coverage", Optional.of(new BigDecimal(value)), true))), List.of());
        assertEquals(expected ? PASSED : FAILED, result.criteria().get(0).status());
    }

    @Test void absentMetricDoesNotBecomeZero() {
        var rubric = draft(criterion("100")).publish();
        var check = new EvaluationCheck(CheckId.create(), rubric.criteria().get(0).id(),
                new MetricCheck("coverage", ComparisonOperator.LTE, BigDecimal.ZERO));
        var result = policy.evaluate(approve(rubric, List.of(check)), snapshot(List.of(
                new MetricObservation(check.id(), "coverage", Optional.empty(), true))), List.of());
        assertEquals(NOT_EVALUABLE, result.criteria().get(0).status());
    }

    @Test void rejectsForeignEvidenceWrongTargetsAndDuplicateObservations() {
        var fixture = fixture();
        var foreign = new RuleObservation(CheckId.create(), "java:S1481", true, true, List.of());
        assertThrows(DomainRuleViolation.class, () -> policy.evaluate(fixture.config, snapshot(List.of(foreign)), List.of()));
        var wrong = new RuleObservation(fixture.check.id(), "java:S999", true, true, List.of());
        assertThrows(DomainRuleViolation.class, () -> policy.evaluate(fixture.config, snapshot(List.of(wrong)), List.of()));
        assertThrows(DomainRuleViolation.class, () -> snapshot(List.of(wrong, wrong)));
        var metric = new MetricObservation(fixture.check.id(), "coverage", Optional.of(BigDecimal.TEN), true);
        assertThrows(DomainRuleViolation.class, () -> policy.evaluate(fixture.config, snapshot(List.of(metric)), List.of()));
    }

    @Test void rejectsManualReviewOfAutomaticCheckAndDuplicateManualReview() {
        var fixture = fixture();
        var decision = new ManualDecision(fixture.check.id(), true, UUID.randomUUID(), NOW, "Review");
        assertThrows(DomainRuleViolation.class, () -> policy.evaluate(fixture.config, snapshot(List.of()), List.of(decision)));
        var rubric = fixture.config.rubric();
        var manual = new EvaluationCheck(CheckId.create(), rubric.criteria().get(0).id(), new ManualCheck("Review"));
        var config = approve(rubric, List.of(manual));
        var manualDecision = new ManualDecision(manual.id(), true, UUID.randomUUID(), NOW, "Approved");
        assertThrows(DomainRuleViolation.class, () -> policy.evaluate(config, snapshot(List.of()),
                List.of(manualDecision, manualDecision)));
    }

    @Test void exactCommitProjectTaskAndBranchAreMandatory() {
        var fixture = fixture();
        var evaluation = evaluation(fixture.config); evaluation.start();
        var foreignReferences = List.of(
                new SonarAnalysisReference(expected(), "analysis", "b".repeat(40)),
                new SonarAnalysisReference(new ExpectedAnalysis("other", "task", scope()), "analysis", SHA),
                new SonarAnalysisReference(new ExpectedAnalysis("project", "other", scope()), "analysis", SHA),
                new SonarAnalysisReference(new ExpectedAnalysis("project", "task",
                        new AnalysisScope(AnalysisScope.Kind.PULL_REQUEST, "1")), "analysis", SHA));
        for (var ref : foreignReferences) {
            assertThrows(DomainRuleViolation.class, () -> evaluation.complete(new AnalysisSnapshot(ref, NOW, List.of()), List.of(), policy));
            assertEquals(EvaluationStatus.RUNNING, evaluation.status());
            assertTrue(evaluation.summary().isEmpty());
        }
    }

    @Test void failureIsNeverAScoreAndRetryKeepsIdentity() {
        var evaluation = evaluation(fixture().config);
        assertThrows(DomainRuleViolation.class, () -> evaluation.complete(snapshot(List.of()), List.of(), policy));
        evaluation.start();
        evaluation.fail("SONAR_UNAVAILABLE");
        assertEquals(EvaluationStatus.FAILED, evaluation.status());
        assertTrue(evaluation.summary().isEmpty());
        var id = evaluation.id();
        evaluation.retry();
        assertEquals(id, evaluation.id());
        assertEquals(EvaluationStatus.PENDING, evaluation.status());
        assertTrue(evaluation.failureCode().isEmpty());
    }

    @Test void valueObjectsRejectInvalidWeightsReferencesAndPercentages() {
        assertThrows(DomainRuleViolation.class, () -> new Weight(BigDecimal.ZERO));
        assertThrows(DomainRuleViolation.class, () -> new Weight(new BigDecimal("101")));
        assertThrows(DomainRuleViolation.class, () -> new RubricVersion(0));
        assertThrows(DomainRuleViolation.class, () -> new CompliancePercentage(new BigDecimal("-1")));
        assertThrows(DomainRuleViolation.class, () -> new SubmissionReference(UUID.randomUUID(), UUID.randomUUID(), "short"));
    }

    private RubricCriterion criterion(String weight) {
        return new RubricCriterion(CriterionId.create(), "Avoid unused variables", "Readable code", new Weight(new BigDecimal(weight)));
    }
    private Rubric draft(RubricCriterion... criteria) {
        return new Rubric(RubricId.create(), new RubricVersion(1), "Java rubric", List.of(criteria));
    }
    private EvaluationCheck rule(RubricCriterion criterion) {
        return new EvaluationCheck(CheckId.create(), criterion.id(), new RuleCheck("java:S1481"));
    }
    private TeacherApproval teacher() { return new TeacherApproval(UUID.randomUUID(), NOW); }
    private RubricEvaluationConfiguration configuration(PublishedRubric rubric, List<EvaluationCheck> checks) {
        return new RubricEvaluationConfiguration(ConfigurationId.create(), rubric,
                new TechnicalPlanReference(UUID.randomUUID(), rubric.id(), rubric.version()), checks);
    }
    private ApprovedConfiguration approve(PublishedRubric rubric, List<EvaluationCheck> checks) {
        var plan = new TechnicalPlanReference(UUID.randomUUID(), rubric.id(), rubric.version());
        var config = new RubricEvaluationConfiguration(ConfigurationId.create(), rubric, plan, checks);
        var automatic = new HashSet<CheckId>();
        checks.stream().filter(c -> !(c.specification() instanceof ManualCheck)).forEach(c -> automatic.add(c.id()));
        return config.approve(teacher(), automatic.isEmpty() ? Optional.empty()
                : Optional.of(new CatalogValidation(plan.planId(), "catalog-v1", automatic, NOW)));
    }
    private AnalysisScope scope() { return new AnalysisScope(AnalysisScope.Kind.BRANCH, "main"); }
    private ExpectedAnalysis expected() { return new ExpectedAnalysis("project", "task", scope()); }
    private AnalysisSnapshot snapshot(List<CheckObservation> observations) {
        return new AnalysisSnapshot(new SonarAnalysisReference(expected(), "analysis-1", SHA), NOW, observations);
    }
    private SubmissionEvaluation evaluation(ApprovedConfiguration config) {
        return new SubmissionEvaluation(EvaluationId.create(),
                new SubmissionReference(UUID.randomUUID(), UUID.randomUUID(), SHA), expected(), config);
    }
    private Fixture fixture() {
        var rubric = draft(criterion("100")).publish();
        var check = rule(rubric.criteria().get(0));
        return new Fixture(approve(rubric, List.of(check)), check);
    }
    private record Fixture(ApprovedConfiguration config, EvaluationCheck check) {}
}
