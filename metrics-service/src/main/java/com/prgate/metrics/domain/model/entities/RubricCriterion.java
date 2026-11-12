package com.prgate.metrics.domain.model.entities;
import com.prgate.metrics.domain.model.valueobjects.*;
public record RubricCriterion(CriterionId id, String description, String objective, Weight weight) {
    public RubricCriterion {
        Checks.required(id); description = Checks.text(description);
        objective = Checks.text(objective); Checks.required(weight);
    }
}
