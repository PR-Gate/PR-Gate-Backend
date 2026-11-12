package com.prgate.metrics.domain.policy;
import com.prgate.metrics.domain.model.enums.CheckStatus;
import com.prgate.metrics.domain.model.valueobjects.*;
import java.math.BigDecimal;
import java.util.*;
import static com.prgate.metrics.domain.model.enums.CheckStatus.*;
public final class CompliancePolicy {
    public EvaluationSummary evaluate(ApprovedConfiguration configuration, AnalysisSnapshot snapshot,
                                      List<ManualDecision> decisions) {
        Checks.required(configuration); Checks.required(snapshot); Checks.required(decisions);
        var byId = new HashMap<CheckId, EvaluationCheck>();
        configuration.checks().forEach(c -> byId.put(c.id(), c));
        var observations = new HashMap<CheckId, CheckObservation>();
        for (var observation : snapshot.observations()) {
            var check = byId.get(observation.checkId());
            Checks.require(check != null, "observation references unknown check");
            Checks.require(matches(check.specification(), observation), "observation does not match configured check");
            observations.put(observation.checkId(), observation);
        }
        var manual = new HashMap<CheckId, ManualDecision>();
        for (var decision : decisions) {
            Checks.required(decision);
            var check = byId.get(decision.checkId());
            Checks.require(check != null && check.specification() instanceof ManualCheck,
                    "manual decision must reference a manual check");
            Checks.require(manual.putIfAbsent(decision.checkId(), decision) == null, "duplicate manual decision");
        }
        var results = new ArrayList<CriterionResult>();
        BigDecimal evaluatedWeight = BigDecimal.ZERO, passedWeight = BigDecimal.ZERO;
        for (var criterion : configuration.rubric().criteria()) {
            var checks = configuration.checks().stream().filter(c -> c.criterionId().equals(criterion.id()))
                    .map(c -> assess(c, observations.get(c.id()), manual.get(c.id()))).toList();
            CheckStatus status = checks.stream().anyMatch(c -> c.status() == NOT_EVALUABLE) ? NOT_EVALUABLE
                    : checks.stream().anyMatch(c -> c.status() == FAILED) ? FAILED : PASSED;
            results.add(new CriterionResult(criterion.id(), criterion.weight(), status, checks));
            if (status != NOT_EVALUABLE) evaluatedWeight = evaluatedWeight.add(criterion.weight().value());
            if (status == PASSED) passedWeight = passedWeight.add(criterion.weight().value());
        }
        var provisional = evaluatedWeight.signum() == 0 ? Optional.<CompliancePercentage>empty()
                : Optional.of(CompliancePercentage.ratio(passedWeight, evaluatedWeight));
        var coverage = new CompliancePercentage(evaluatedWeight);
        var finalScore = evaluatedWeight.compareTo(new BigDecimal("100")) == 0 ? provisional
                : Optional.<CompliancePercentage>empty();
        return new EvaluationSummary(results, coverage, provisional, finalScore);
    }
    private boolean matches(CheckSpecification spec, CheckObservation observation) {
        return (spec instanceof RuleCheck rule && observation instanceof RuleObservation observed
                    && rule.ruleKey().equals(observed.ruleKey()))
            || (spec instanceof MetricCheck metric && observation instanceof MetricObservation measure
                    && metric.metricKey().equals(measure.metricKey()));
    }
    private CheckResult assess(EvaluationCheck check, CheckObservation observation, ManualDecision decision) {
        if (check.specification() instanceof ManualCheck) {
            return decision == null ? result(check, NOT_EVALUABLE, "manual review pending")
                    : result(check, decision.passed() ? PASSED : FAILED, "teacher decision recorded");
        }
        if (observation == null) return result(check, NOT_EVALUABLE, "missing observation");
        if (observation instanceof RuleObservation rule) {
            if (!rule.ruleExecuted() || !rule.evidenceComplete())
                return result(check, NOT_EVALUABLE, "rule execution or complete evidence not verified");
            return result(check, rule.issues().isEmpty() ? PASSED : FAILED,
                    rule.issues().isEmpty() ? "no violations in verified scope" : "rule violations found");
        }
        var metric = (MetricObservation) observation;
        if (!metric.evidenceComplete() || metric.value().isEmpty())
            return result(check, NOT_EVALUABLE, "metric value or complete evidence missing");
        boolean passed = ((MetricCheck) check.specification()).accepts(metric.value().orElseThrow());
        return result(check, passed ? PASSED : FAILED, passed ? "threshold met" : "threshold not met");
    }
    private CheckResult result(EvaluationCheck check, CheckStatus status, String reason) {
        return new CheckResult(check.id(), status, reason);
    }
}
