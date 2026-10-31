package com.prgate.ai.domain.model.aggregates;

import java.util.*;
import com.prgate.ai.domain.model.enums.*;
import com.prgate.ai.domain.model.valueobjects.*;
import static com.prgate.ai.domain.model.valueobjects.Checks.*;

public final class LearningTest {
    private final LearningTestId id;
    private final PlanId planId;
    private final RubricReference rubric;
    private final String studentId;
    private final TestKind kind;
    private final AssessmentId assessmentId;
    private final List<LearningQuestion> questions;
    private TestStatus status = TestStatus.DRAFT;

    private LearningTest(LearningTestId id, PlanId planId, RubricReference rubric, String studentId, TestKind kind,
            AssessmentId assessmentId, List<LearningQuestion> questions, Set<CriterionId> criteria,
            Map<FindingId, CriterionId> evidence) {
        this.id = present(id);
        this.planId = present(planId);
        this.rubric = present(rubric);
        this.studentId = text(studentId);
        this.kind = kind;
        this.assessmentId = assessmentId;
        this.questions = List.copyOf(present(questions));
        require(!this.questions.isEmpty(), "test requires questions");
        Set<QuestionId> ids = new HashSet<>();
        this.questions.forEach(q -> {
            require(ids.add(q.id()), "duplicate question");
            require(criteria.contains(q.criterionId()), "unknown question criterion");
            if (kind == TestKind.INITIAL)
                require(q.evidenceIds().isEmpty(), "initial test cannot reference findings");
            else
                require(!q.evidenceIds().isEmpty(), "reinforcement question requires evidence");
            q.evidenceIds().forEach(key -> require(q.criterionId().equals(evidence.get(key)),
                    "question evidence must exist and match criterion"));
        });
    }

    public static LearningTest initial(LearningTestId id, String studentId, TechnicalAssessmentPlan plan,
            List<LearningQuestion> questions) {
        present(plan);
        require(plan.status() == PlanStatus.APPROVED, "initial test requires approved plan");
        Set<CriterionId> criteria = Set.copyOf(plan.criteria().stream().map(RubricCriterion::id).toList());
        return new LearningTest(id, plan.id(), plan.rubric(), studentId, TestKind.INITIAL, null, questions, criteria,
                Map.of());
    }

    public static LearningTest reinforcement(LearningTestId id, Assessment assessment,
            List<LearningQuestion> questions) {
        present(assessment);
        require(assessment.status() == AssessmentStatus.COMPLETED, "reinforcement requires completed feedback");
        Map<FindingId, CriterionId> evidence = new HashMap<>();
        assessment.findings().forEach(f -> evidence.put(f.id(), f.criterionId()));
        return new LearningTest(id, assessment.planId(), assessment.rubric(), assessment.submission().studentId(),
                TestKind.REINFORCEMENT, assessment.id(), questions, assessment.criterionIds(), evidence);
    }

    public void publish() {
        require(status == TestStatus.DRAFT, "only draft tests can be published");
        status = TestStatus.PUBLISHED;
    }

    public LearningTestId id() {
        return id;
    }

    public PlanId planId() {
        return planId;
    }

    public RubricReference rubric() {
        return rubric;
    }

    public String studentId() {
        return studentId;
    }

    public TestKind kind() {
        return kind;
    }

    public Optional<AssessmentId> assessmentId() {
        return Optional.ofNullable(assessmentId);
    }

    public List<LearningQuestion> questions() {
        return questions;
    }

    public TestStatus status() {
        return status;
    }
}