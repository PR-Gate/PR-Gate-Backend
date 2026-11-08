package com.prgate.ai.application.service;

import com.prgate.ai.application.dto.*;
import com.prgate.ai.application.exception.*;
import com.prgate.ai.application.port.in.*;
import com.prgate.ai.domain.model.aggregates.Assessment;
import com.prgate.ai.domain.model.enums.AssessmentStatus;
import com.prgate.ai.domain.model.valueobjects.*;
import com.prgate.ai.domain.repository.*;
import static com.prgate.ai.domain.model.valueobjects.Checks.*;

public final class AssessmentService implements AssessmentUseCases {
    private final AssessmentRepository assessments;
    private final TechnicalAssessmentPlanRepository plans;
    private final GenerateFeedbackUseCase feedback;
    public AssessmentService(AssessmentRepository assessments, TechnicalAssessmentPlanRepository plans,
                             GenerateFeedbackUseCase feedback) {
        this.assessments = present(assessments);
        this.plans = present(plans);
        this.feedback = present(feedback);
    }
    public AssessmentDetails record(RecordAnalysisCommand command) {
        present(command);
        var plan = plans.findById(command.planId())
                .orElseThrow(() -> new TechnicalPlanNotFoundException(command.planId()));
        var assessment = new Assessment(AssessmentId.create(), command.submission(), plan);
        assessment.startAnalysis();
        assessment.completeAnalysis(command.analysisId(), command.submission(), command.findings());
        assessments.save(assessment);
        return AssessmentDetails.from(assessment);
    }
    public AssessmentDetails get(AssessmentId id) { return AssessmentDetails.from(load(id)); }
    public AssessmentFeedbackResult retryFeedback(AssessmentId id) {
        var assessment = load(id);
        require(assessment.status() == AssessmentStatus.FAILED
                && assessment.failedAt().orElse(null) == AssessmentStatus.ANALYZED,
                "only failed feedback generation can be retried");
        assessment.retry();
        assessments.save(assessment);
        return feedback.generate(id);
    }
    private Assessment load(AssessmentId id) {
        present(id);
        return assessments.findById(id).orElseThrow(() -> new AssessmentNotFoundException(id));
    }
}
