package com.prgate.ai.application.dto;

import java.util.List;
import com.prgate.ai.domain.model.valueobjects.*;

/** Input to the generator. The generator proposes checks, not plan IDs or approval state. */
public record RubricTranslationRequest(RubricReference rubric, SourceLanguage language,
                                        List<RubricCriterion> criteria) {
    public RubricTranslationRequest {
        var validated = new TranslateRubricCommand(rubric, language, criteria);
        criteria = validated.criteria();
    }
}
