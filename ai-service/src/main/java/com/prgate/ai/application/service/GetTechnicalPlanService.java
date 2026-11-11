package com.prgate.ai.application.service;

import com.prgate.ai.application.dto.TechnicalPlanResult;
import com.prgate.ai.application.exception.TechnicalPlanNotFoundException;
import com.prgate.ai.application.port.in.GetTechnicalPlanUseCase;
import com.prgate.ai.domain.model.valueobjects.PlanId;
import com.prgate.ai.domain.repository.TechnicalAssessmentPlanRepository;
import static com.prgate.ai.domain.model.valueobjects.Checks.present;

public final class GetTechnicalPlanService implements GetTechnicalPlanUseCase {
    private final TechnicalAssessmentPlanRepository plans;

    public GetTechnicalPlanService(TechnicalAssessmentPlanRepository plans) { this.plans = present(plans); }

    @Override
    public TechnicalPlanResult get(PlanId id) {
        present(id);
        return plans.findById(id).map(TechnicalPlanResult::from)
                .orElseThrow(() -> new TechnicalPlanNotFoundException(id));
    }
}
