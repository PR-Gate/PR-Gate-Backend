package com.prgate.ai.application.dto;

import java.util.List;
import com.prgate.ai.domain.model.valueobjects.TechnicalCheck;
import static com.prgate.ai.domain.model.valueobjects.Checks.present;

public record RubricTranslationResult(List<TechnicalCheck> checks) {
    public RubricTranslationResult {
        checks = List.copyOf(present(checks));
    }
}
