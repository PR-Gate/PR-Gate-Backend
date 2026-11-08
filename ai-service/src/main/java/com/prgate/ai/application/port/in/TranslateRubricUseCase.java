package com.prgate.ai.application.port.in;

import com.prgate.ai.application.dto.TranslateRubricCommand;
import com.prgate.ai.application.dto.TechnicalPlanResult;

public interface TranslateRubricUseCase {
    TechnicalPlanResult translate(TranslateRubricCommand command);
}
