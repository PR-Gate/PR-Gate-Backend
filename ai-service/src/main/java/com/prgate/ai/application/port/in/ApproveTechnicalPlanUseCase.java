package com.prgate.ai.application.port.in;

import com.prgate.ai.application.dto.ApproveTechnicalPlanCommand;
import com.prgate.ai.application.dto.TechnicalPlanResult;

public interface ApproveTechnicalPlanUseCase {
    TechnicalPlanResult approve(ApproveTechnicalPlanCommand command);
}
