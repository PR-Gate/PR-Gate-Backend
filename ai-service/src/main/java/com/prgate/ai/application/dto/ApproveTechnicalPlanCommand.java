package com.prgate.ai.application.dto;

import com.prgate.ai.domain.model.valueobjects.PlanId;
import static com.prgate.ai.domain.model.valueobjects.Checks.present;

public record ApproveTechnicalPlanCommand(PlanId planId) {
    public ApproveTechnicalPlanCommand { present(planId); }
}
