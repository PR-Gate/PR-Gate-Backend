package com.prgate.ai.application.service;

import com.prgate.ai.application.dto.*;
import com.prgate.ai.application.exception.*;
import com.prgate.ai.application.port.in.GenerateFeedbackUseCase;
import com.prgate.ai.application.port.out.*;
import com.prgate.ai.domain.exception.DomainRuleViolation;
import com.prgate.ai.domain.model.enums.AssessmentStatus;
import com.prgate.ai.domain.model.valueobjects.AssessmentId;
import com.prgate.ai.domain.repository.*;
import static com.prgate.ai.domain.model.valueobjects.Checks.*;

public final class GenerateFeedbackService implements GenerateFeedbackUseCase {
    private final AssessmentRepository assessments;
    private final TechnicalAssessmentPlanRepository plans;
    private final FeedbackGenerator generator;

    public GenerateFeedbackService(AssessmentRepository assessments,
                                   TechnicalAssessmentPlanRepository plans, FeedbackGenerator generator) {
        this.assessments = present(assessments);
        this.plans = present(plans);
        this.generator = present(generator);
    }

    @Override
    public AssessmentFeedbackResult generate(AssessmentId id) {
        present(id);
        var assessment = assessments.findById(id).orElseThrow(() -> new AssessmentNotFoundException(id));
        require(assessment.status() == AssessmentStatus.ANALYZED, "feedback requires analyzed assessment");
        var plan = plans.findById(assessment.planId())
                .orElseThrow(() -> new TechnicalPlanNotFoundException(assessment.planId()));
        require(plan.rubric().equals(assessment.rubric()), "assessment rubric differs from plan");
        var request = new FeedbackRequest(plan.criteria(), assessment.findings());
        FeedbackResult result;
        try {
            result = generator.generate(request);
            if (result == null) throw new FeedbackGenerationException("Feedback generation returned no result");
            assessment.completeFeedback(result.summary(), result.feedback());
        } catch (FeedbackGenerationException | AiGenerationException | DomainRuleViolation ex) {
            // Persist only a safe category, not untrusted provider messages.
            assessment.fail("FEEDBACK_GENERATION_FAILED");
            assessments.save(assessment);
            if (ex instanceof AiGenerationException failure) throw failure;
            if (ex instanceof FeedbackGenerationException failure) throw failure;
            throw new FeedbackGenerationException("Generated feedback contains invalid evidence");
        }
        assessments.save(assessment);
        return new AssessmentFeedbackResult(id, result);
    }
}
