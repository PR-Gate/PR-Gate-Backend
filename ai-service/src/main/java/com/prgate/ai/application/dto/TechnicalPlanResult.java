package com.prgate.ai.application.dto;

import java.util.List;
import java.util.Optional;
import com.prgate.ai.domain.model.aggregates.TechnicalAssessmentPlan;
import com.prgate.ai.domain.model.enums.PlanStatus;
import com.prgate.ai.domain.model.valueobjects.*;
import static com.prgate.ai.domain.model.valueobjects.Checks.*;

/** Immutable projection; returning it does not expose a mutable aggregate. */
public record TechnicalPlanResult(PlanId id, RubricReference rubric, SourceLanguage language,
                                   PlanStatus status, List<RubricCriterion> criteria,
                                   List<TechnicalCheck> checks, Optional<String> catalogVersion) {
    public TechnicalPlanResult {
        present(id);
        present(rubric);
        present(language);
        present(status);
        criteria = List.copyOf(present(criteria));
        checks = List.copyOf(present(checks));
        present(catalogVersion);
    }

    public static TechnicalPlanResult from(TechnicalAssessmentPlan plan) {
        present(plan);
        return new TechnicalPlanResult(plan.id(), plan.rubric(), plan.language(),
                plan.status(), plan.criteria(), plan.checks(), plan.catalogVersion());
    }
}
