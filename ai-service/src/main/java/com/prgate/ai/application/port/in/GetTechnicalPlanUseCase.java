package com.prgate.ai.application.port.in;

import com.prgate.ai.application.dto.TechnicalPlanResult;
import com.prgate.ai.domain.model.valueobjects.PlanId;

public interface GetTechnicalPlanUseCase {
    TechnicalPlanResult get(PlanId id);
}
