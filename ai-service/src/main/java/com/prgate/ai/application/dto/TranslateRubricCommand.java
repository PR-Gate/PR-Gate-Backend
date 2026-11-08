package com.prgate.ai.application.dto;

import java.util.HashSet;
import java.util.List;
import com.prgate.ai.domain.model.valueobjects.*;
import static com.prgate.ai.domain.model.valueobjects.Checks.*;

public record TranslateRubricCommand(RubricReference rubric, SourceLanguage language,
                                      List<RubricCriterion> criteria) {
    public TranslateRubricCommand {
        present(rubric);
        present(language);
        criteria = List.copyOf(present(criteria));
        require(!criteria.isEmpty(), "rubric requires criteria");
        var ids = new HashSet<CriterionId>();
        criteria.forEach(c -> require(ids.add(c.id()), "duplicate criterion"));
    }
}
