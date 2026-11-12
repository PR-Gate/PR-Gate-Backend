package com.prgate.metrics.domain.model.aggregates;
import com.prgate.metrics.domain.model.enums.EvaluationStatus;
import com.prgate.metrics.domain.model.valueobjects.*;
import com.prgate.metrics.domain.policy.CompliancePolicy;
import java.util.*;
import static com.prgate.metrics.domain.model.enums.EvaluationStatus.*;
public final class SubmissionEvaluation {
    private final EvaluationId id;
    private final SubmissionReference submission;
    private final ExpectedAnalysis expectedAnalysis;
    private final ApprovedConfiguration configuration;
    private EvaluationStatus status = PENDING;
    private AnalysisSnapshot analysis;
    private List<ManualDecision> decisions = List.of();
    private EvaluationSummary summary;
    private String failureCode;
    public SubmissionEvaluation(EvaluationId id, SubmissionReference submission, ExpectedAnalysis expectedAnalysis,
                                ApprovedConfiguration configuration) {
        this.id = Checks.required(id); this.submission = Checks.required(submission);
        this.expectedAnalysis = Checks.required(expectedAnalysis); this.configuration = Checks.required(configuration);
    }
    public void start() {
        Checks.require(status == PENDING, "only pending evaluations can start");
        status = RUNNING;
    }
    public void complete(AnalysisSnapshot snapshot, List<ManualDecision> decisions, CompliancePolicy policy) {
        Checks.require(status == RUNNING, "evaluation must be running");
        Checks.required(snapshot); Checks.required(policy);
        Checks.require(snapshot.reference().source().equals(expectedAnalysis), "analysis project, task or scope mismatch");
        Checks.require(snapshot.reference().commitSha().equals(submission.commitSha()), "analysis commit mismatch");
        var copy = List.copyOf(Checks.required(decisions));
        var result = policy.evaluate(configuration, snapshot, copy);
        this.analysis = snapshot; this.decisions = copy; this.summary = result;
        status = result.finalPercentage().isPresent() ? COMPLETED : PARTIAL;
    }
    public void recordManualReview(ManualDecision decision, CompliancePolicy policy) {
        Checks.require(status == PARTIAL, "manual review requires a partial evaluation");
        Checks.required(decision); Checks.required(policy);
        Checks.require(decisions.stream().noneMatch(d -> d.checkId().equals(decision.checkId())),
                "manual check already reviewed");
        var copy = new ArrayList<>(decisions); copy.add(decision);
        var result = policy.evaluate(configuration, analysis, copy);
        decisions = List.copyOf(copy); summary = result;
        status = result.finalPercentage().isPresent() ? COMPLETED : PARTIAL;
    }
    public void fail(String code) {
        Checks.require(status == RUNNING, "only a running evaluation can fail");
        failureCode = Checks.text(code); status = FAILED;
    }
    public void retry() {
        Checks.require(status == FAILED, "only failed evaluations can be retried");
        failureCode = null; status = PENDING;
    }
    public EvaluationId id() { return id; }
    public SubmissionReference submission() { return submission; }
    public ExpectedAnalysis expectedAnalysis() { return expectedAnalysis; }
    public ApprovedConfiguration configuration() { return configuration; }
    public EvaluationStatus status() { return status; }
    public Optional<AnalysisSnapshot> analysis() { return Optional.ofNullable(analysis); }
    public List<ManualDecision> decisions() { return decisions; }
    public Optional<EvaluationSummary> summary() { return Optional.ofNullable(summary); }
    public Optional<String> failureCode() { return Optional.ofNullable(failureCode); }
}
