package com.prgate.ai.domain.model.aggregates;

import java.util.*;
import com.prgate.ai.domain.model.enums.*;
import com.prgate.ai.domain.model.valueobjects.*;
import static com.prgate.ai.domain.model.valueobjects.Checks.*;

/**
 * Analysis and feedback lifecycle; test generation has a separate aggregate
 * lifecycle.
 */
public final class Assessment {
  private final AssessmentId id;
  private final SubmissionReference submission;
  private final PlanId planId;
  private final RubricReference rubric;
  private final Set<CriterionId> criterionIds;
  private final Map<CriterionId, Set<String>> findingTargets;
  private AssessmentStatus status = AssessmentStatus.PENDING;
  private AssessmentStatus failedAt;
  private String analysisId, failureReason, summary;
  private List<TechnicalFinding> findings = List.of();
  private List<FeedbackItem> feedback = List.of();

  public Assessment(AssessmentId id, SubmissionReference submission, TechnicalAssessmentPlan plan) {
    this.id = present(id);
    this.submission = present(submission);
    present(plan);
    require(plan.status() == PlanStatus.APPROVED, "assessment requires approved plan");
    planId = plan.id();
    rubric = plan.rubric();
    criterionIds = Set.copyOf(plan.criteria().stream().map(RubricCriterion::id).toList());
    Map<CriterionId, Set<String>> targets = new HashMap<>();
    plan.checks().stream()
        .filter(c -> c.mechanism() == CheckMechanism.SONAR_RULE || c.mechanism() == CheckMechanism.EXTERNAL_CHECK)
        .forEach(c -> targets.computeIfAbsent(c.criterionId(), key -> new HashSet<>()).add(c.target()));
    Map<CriterionId, Set<String>> snapshot = new HashMap<>();
    targets.forEach((key, value) -> snapshot.put(key, Set.copyOf(value)));
    findingTargets = Map.copyOf(snapshot);
  }

  public void startAnalysis() {
    require(status == AssessmentStatus.PENDING, "analysis must start from pending");
    status = AssessmentStatus.ANALYZING;
  }

  public void completeAnalysis(String analysisId, SubmissionReference snapshot, List<TechnicalFinding> findings) {
    require(status == AssessmentStatus.ANALYZING, "analysis is not running");
    require(submission.equals(snapshot), "analysis belongs to different submission revision");
    String acceptedId = text(analysisId);
    List<TechnicalFinding> accepted = List.copyOf(present(findings));
    Set<FindingId> ids = new HashSet<>();
    accepted.forEach(f -> {
      require(ids.add(f.id()), "duplicate finding");
      require(criterionIds.contains(f.criterionId()), "unknown finding criterion");
      require(findingTargets.getOrDefault(f.criterionId(), Set.of()).contains(f.ruleKey()),
          "finding rule is not configured for criterion");
    });
    this.analysisId = acceptedId;
    this.findings = accepted;
    status = AssessmentStatus.ANALYZED;
  }

  public void completeFeedback(String summary, List<FeedbackItem> feedback) {
    require(status == AssessmentStatus.ANALYZED, "completed analysis required");
    String acceptedSummary = text(summary);
    List<FeedbackItem> accepted = List.copyOf(present(feedback));
    Map<FindingId, CriterionId> evidence = new HashMap<>();
    findings.forEach(f -> evidence.put(f.id(), f.criterionId()));
    accepted.forEach(item -> {
      require(criterionIds.contains(item.criterionId()), "unknown feedback criterion");
      item.evidenceIds().forEach(key -> require(item.criterionId().equals(evidence.get(key)),
          "feedback evidence must exist and match criterion"));
    });
    this.summary = acceptedSummary;
    this.feedback = accepted;
    status = AssessmentStatus.COMPLETED;
  }

  public void fail(String reason) {
    require(status == AssessmentStatus.ANALYZING || status == AssessmentStatus.ANALYZED, "only active work can fail");
    String accepted = text(reason);
    failedAt = status;
    failureReason = accepted;
    status = AssessmentStatus.FAILED;
  }

  public void retry() {
    require(status == AssessmentStatus.FAILED, "only failed work can retry");
    status = failedAt == AssessmentStatus.ANALYZING ? AssessmentStatus.PENDING : AssessmentStatus.ANALYZED;
    failedAt = null;
    failureReason = null;
  }

  public AssessmentId id() {
    return id;
  }

  public SubmissionReference submission() {
    return submission;
  }

  public PlanId planId() {
    return planId;
  }

  public RubricReference rubric() {
    return rubric;
  }

  public Set<CriterionId> criterionIds() {
    return criterionIds;
  }

  public AssessmentStatus status() {
    return status;
  }

  public List<TechnicalFinding> findings() {
    return findings;
  }

  public List<FeedbackItem> feedback() {
    return feedback;
  }

  public Optional<String> analysisId() {
    return Optional.ofNullable(analysisId);
  }

  public Optional<String> summary() {
    return Optional.ofNullable(summary);
  }

  public Optional<String> failureReason() {
    return Optional.ofNullable(failureReason);
  }

  public Optional<AssessmentStatus> failedAt() {
    return Optional.ofNullable(failedAt);
  }
}