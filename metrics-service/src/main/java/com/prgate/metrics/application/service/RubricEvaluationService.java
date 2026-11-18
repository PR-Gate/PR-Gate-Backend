package com.prgate.metrics.application.service;

import com.prgate.metrics.application.dto.*;
import com.prgate.metrics.application.port.out.*;
import com.prgate.metrics.domain.model.aggregates.Rubric;
import com.prgate.metrics.domain.model.entities.RubricCriterion;
import com.prgate.metrics.domain.model.valueobjects.*;
import com.prgate.metrics.domain.policy.CompliancePolicy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.*;

@Service
public class RubricEvaluationService {
    private final EvaluationStore store;
    private final ProjectAccessPort access;
    private final SonarEvidencePort sonar;
    private final CompliancePolicy policy = new CompliancePolicy();
    public RubricEvaluationService(EvaluationStore store, ProjectAccessPort access, SonarEvidencePort sonar) {
        this.store = store; this.access = access; this.sonar = sonar;
    }

    @Transactional
    public PublishedRubric publish(UUID actor, UUID project, UUID previousId, String title, List<RubricCriterion> criteria) {
        require(actor, project, true);
        UUID id = previousId == null ? UUID.randomUUID() : previousId;
        int previousVersion = store.latestRubricVersion(project, id);
        if (previousId != null && previousVersion == 0) throw new NoSuchElementException();
        var rubric = new Rubric(new RubricId(id), new RubricVersion(previousVersion + 1), title, criteria).publish();
        store.saveRubric(project, rubric, actor);
        return rubric;
    }

    public StoredConfiguration approve(UUID actor, UUID project, UUID rubricId, int version, long repositoryId,
            String gitRef, UUID planId, List<StoredConfiguration.CheckDefinition> checks) {
        var allowed = require(actor, project, true);
        if (allowed.organization() == null || allowed.organization().isBlank())
            throw new IllegalArgumentException("Project requires its GitHub organization");
        if (repositoryId <= 0 || gitRef == null || !gitRef.matches("refs/heads/[^\\s]+"))
            throw new IllegalArgumentException("Repository and full branch reference are required");
        var rubric = store.rubric(project, rubricId, version).orElseThrow(NoSuchElementException::new);
        for (var check : checks) {
            check.toDomain();
            if (check.type().equals("METRIC")) {
                if (!SupportedMetrics.KEYS.contains(check.key()) || check.threshold().signum() < 0
                        || (Set.of("coverage", "duplicated_lines_density").contains(check.key())
                        && check.threshold().compareTo(java.math.BigDecimal.valueOf(100)) > 0))
                    throw new IllegalArgumentException("Unsupported metric or threshold");
            } else if (check.type().equals("RULE")) sonar.validateRule(check.key());
        }
        var result = new StoredConfiguration(UUID.randomUUID(), project, repositoryId, allowed.organization(),
                gitRef, rubric, planId, checks, actor, Instant.now(), "sonar-global-v1");
        result.basis(); // All published criteria and exact automatic check IDs must be covered.
        QualityGateRequest.conditions(result);
        store.activateConfiguration(result);
        return result;
    }

    public EvaluationStore.StoredEvaluation evaluate(UUID actor, UUID project, UUID configurationId, UUID eventId) {
        var allowed = require(actor, project, true);
        var configuration = store.configuration(configurationId).orElseThrow(NoSuchElementException::new);
        if (!configuration.projectId().equals(project) || allowed.organization() == null
                || !configuration.organization().equalsIgnoreCase(allowed.organization())) throw new EvaluationAccessDenied();
        return evaluateTrusted(configuration, eventId);
    }

    /** Scheduled path consumes only stored approved configurations, never caller-supplied policy. */
    public EvaluationStore.StoredEvaluation evaluateTrusted(StoredConfiguration configuration, UUID eventId) {
        var event = store.analysis(eventId).orElseThrow(NoSuchElementException::new);
        requireMatchingRepository(configuration, event);
        var evidence = store.evidence(eventId);
        return store.evaluateLocked(configuration, event,
                decisions -> calculate(configuration, event, evidence, decisions), evidence);
    }

    public List<EvaluationStore.StoredEvaluation> list(UUID actor, UUID project, long repositoryId, int page, int size) {
        require(actor, project, false);
        if (repositoryId <= 0 || page < 0 || size < 1 || size > 100 || page > 100000)
            throw new IllegalArgumentException("Invalid repository or pagination");
        return store.evaluations(project, repositoryId, page, size);
    }

    public PublishedRubric rubric(UUID actor, UUID project, UUID id, int version) {
        require(actor, project, false);
        return store.rubric(project, id, version).orElseThrow(NoSuchElementException::new);
    }

    public StoredConfiguration configuration(UUID actor, UUID project, UUID id) {
        require(actor, project, false);
        var configuration = store.configuration(id).orElseThrow(NoSuchElementException::new);
        if (!configuration.projectId().equals(project)) throw new NoSuchElementException();
        return configuration;
    }

    public AnalysisView analysis(UUID actor, UUID project, UUID evaluationId) {
        var evaluation = get(actor, project, evaluationId);
        var event = store.analysis(evaluation.eventId()).orElseThrow(NoSuchElementException::new);
        return new AnalysisView(event, store.evidence(event.eventId()));
    }
    public AnalysisView retryEvidence(UUID actor,UUID project,UUID evaluationId) {
        require(actor,project,true);
        var evaluation=get(actor,project,evaluationId);
        store.retryEvidence(evaluation.eventId());
        return analysis(actor,project,evaluationId);
    }

    public record AnalysisView(AnalysisCompletedEvent reference, EvaluationStore.Evidence evidence) { }
    public GradeEstimationEvidence estimationEvidence(UUID actor,UUID project,UUID evaluationId) {
        var evaluation=get(actor,project,evaluationId);
        var configuration=store.configuration(evaluation.configurationId()).orElseThrow(NoSuchElementException::new);
        var evidence=store.evidence(evaluation.eventId());
        List<GradeEstimationEvidence.CriterionEvidence> criteria=new ArrayList<>();
        for (var criterion:configuration.rubric().criteria()) {
            var result=evaluation.summary().criteria().stream().filter(c->c.criterionId().equals(criterion.id())).findFirst().orElseThrow();
            List<GradeEstimationEvidence.CheckEvidence> checks=new ArrayList<>();
            for (var check:configuration.checks()) if (check.criterionId().equals(criterion.id().value())) {
                var outcome=result.checks().stream().filter(c->c.checkId().value().equals(check.id())).findFirst().orElseThrow();
                String reason=outcome.reason();
                for (var decision:evaluation.decisions()) if (decision.checkId().value().equals(check.id())) reason=decision.justification();
                checks.add(new GradeEstimationEvidence.CheckEvidence(check.id(),check.type(),outcome.status().name(),check.key(),
                        evidence.measures().get(check.key()),check.operator()==null?null:check.operator().name(),check.threshold(),reason));
            }
            criteria.add(new GradeEstimationEvidence.CriterionEvidence(criterion.id().value(),criterion.description()+". "+criterion.objective(),
                    criterion.weight().value().divide(java.math.BigDecimal.valueOf(5)),
                    result.status()!=com.prgate.metrics.domain.model.enums.CheckStatus.NOT_EVALUABLE
                            && checks.stream().filter(c->c.type().equals("METRIC")).allMatch(c->evidence.state().equals("COLLECTED")
                            && c.observedValue()!=null),List.copyOf(checks)));
        }
        return new GradeEstimationEvidence(evaluation.id(),configuration.id(),evaluation.commitSha(),
                configuration.rubric().id().value(),configuration.rubric().version().value(),List.copyOf(criteria));
    }
    public Map<String, Object> gateStatus(UUID actor, UUID project, UUID configurationId) {
        configuration(actor, project, configurationId);
        return store.gateStatus(configurationId);
    }
    public Map<String,Object> retryGate(UUID actor,UUID project,UUID configurationId) {
        var rights=require(actor,project,true);
        var configuration=store.configuration(configurationId).orElseThrow(NoSuchElementException::new);
        if (!configuration.projectId().equals(project) || rights.organization()==null
                || !rights.organization().equalsIgnoreCase(configuration.organization())) throw new EvaluationAccessDenied();
        store.retryGate(configurationId);
        return store.gateStatus(configurationId);
    }

    public EvaluationStore.StoredEvaluation get(UUID actor, UUID project, UUID id) {
        require(actor, project, false);
        var value = store.evaluation(id).orElseThrow(NoSuchElementException::new);
        if (!value.projectId().equals(project)) throw new NoSuchElementException();
        return value;
    }

    public EvaluationStore.StoredEvaluation review(UUID actor, UUID project, UUID id, UUID checkId,
                                                    boolean passed, String reason) {
        require(actor, project, true);
        return store.reviewLocked(id, previous -> {
            if (!previous.projectId().equals(project)) throw new NoSuchElementException();
            var configuration = store.configuration(previous.configurationId()).orElseThrow(NoSuchElementException::new);
            if (configuration.basis().checks().stream().noneMatch(c -> c.id().value().equals(checkId)
                    && c.specification() instanceof ManualCheck)) throw new IllegalArgumentException("Not a manual check");
            if (previous.decisions().stream().anyMatch(d -> d.checkId().value().equals(checkId)))
                throw new IllegalArgumentException("Manual check already reviewed");
            var decisions = new ArrayList<>(previous.decisions());
            decisions.add(new ManualDecision(new CheckId(checkId), passed, actor, Instant.now(), reason));
            var event = store.analysis(previous.eventId()).orElseThrow(NoSuchElementException::new);
            var evidence = store.evidence(event.eventId());
            var summary = calculate(configuration, event, evidence, decisions);
            return new EvaluationStore.StoredEvaluation(previous.id(), project, configuration.id(), event.eventId(),
                    event.repositoryId(), event.gitRef(), event.commitSha(),
                    summary.finalPercentage().isPresent() ? "COMPLETED" : "PARTIAL", summary, List.copyOf(decisions), evidence.state());
        });
    }

    private EvaluationSummary calculate(StoredConfiguration configuration, AnalysisCompletedEvent event,
                                        EvaluationStore.Evidence evidence, List<ManualDecision> decisions) {
        List<CheckObservation> observations = new ArrayList<>();
        for (var check : configuration.basis().checks()) {
            if (check.specification() instanceof MetricCheck metric) {
                var value = Optional.ofNullable(evidence.measures().get(metric.metricKey()));
                observations.add(new MetricObservation(check.id(), metric.metricKey(), value,
                        evidence.state().equals("COLLECTED") && value.isPresent()));
            }
            // Rule absence is never proof of execution; leave it NOT_EVALUABLE without verified evidence.
        }
        var scope = new AnalysisScope(AnalysisScope.Kind.BRANCH, event.gitRef().substring("refs/heads/".length()));
        var snapshot = new AnalysisSnapshot(new SonarAnalysisReference(
                new ExpectedAnalysis(event.sonarProjectKey(), event.sonarTaskId(), scope),
                event.sonarAnalysisId(), event.commitSha()), event.analyzedAt(), observations);
        return policy.evaluate(configuration.basis(), snapshot, decisions);
    }

    private ProjectAccessPort.Access require(UUID actor, UUID project, boolean manage) {
        var result = access.check(project, actor);
        if (!result.mayRead() || (manage && !result.mayManage())) throw new EvaluationAccessDenied();
        return result;
    }

    private void requireMatchingRepository(StoredConfiguration configuration, AnalysisCompletedEvent event) {
        String[] repository = event.repository().split("/", -1);
        if (repository.length != 2 || !repository[0].equalsIgnoreCase(configuration.organization())
                || configuration.repositoryId() != event.repositoryId() || !configuration.gitRef().equals(event.gitRef()))
            throw new IllegalArgumentException("Analysis does not belong to configured repository and branch");
    }
}
