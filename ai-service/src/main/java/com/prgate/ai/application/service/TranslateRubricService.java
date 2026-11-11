package com.prgate.ai.application.service;

import com.prgate.ai.application.dto.*;
import com.prgate.ai.application.exception.InvalidTechnicalProposalException;
import com.prgate.ai.application.port.in.TranslateRubricUseCase;
import com.prgate.ai.application.port.out.RubricTranslationPort;
import com.prgate.ai.domain.exception.DomainRuleViolation;
import com.prgate.ai.domain.model.aggregates.TechnicalAssessmentPlan;
import com.prgate.ai.domain.model.valueobjects.PlanId;
import com.prgate.ai.domain.repository.TechnicalAssessmentPlanRepository;
import static com.prgate.ai.domain.model.valueobjects.Checks.present;

public final class TranslateRubricService implements TranslateRubricUseCase {
    private final RubricTranslationPort translator;
    private final TechnicalAssessmentPlanRepository plans;

    public TranslateRubricService(RubricTranslationPort translator, TechnicalAssessmentPlanRepository plans) {
        this.translator = present(translator);
        this.plans = present(plans);
    }

    @Override
    public TechnicalPlanResult translate(TranslateRubricCommand command) {
        present(command);
        TechnicalAssessmentPlan plan;
        try {
            var proposal = translator.translate(new RubricTranslationRequest(
                    command.rubric(), command.language(), command.criteria()));
            if (proposal == null) throw new InvalidTechnicalProposalException();
            plan = new TechnicalAssessmentPlan(PlanId.create(), command.rubric(), command.language(),
                    command.criteria(), proposal.checks());
        } catch (DomainRuleViolation | NullPointerException ex) {
            // Invalid provider DTOs/proposals cannot be persisted as successful plans.
            throw new InvalidTechnicalProposalException();
        }
        plans.save(plan);
        return TechnicalPlanResult.from(plan);
    }
}
