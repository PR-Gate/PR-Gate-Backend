package com.prgate.metrics.domain.model.valueobjects;
import com.prgate.metrics.domain.model.entities.RubricCriterion;
import java.math.BigDecimal;
import java.util.*;
/** Immutable version used by approved configurations and historical evaluations. */
public record PublishedRubric(RubricId id, RubricVersion version, String title,
                             List<RubricCriterion> criteria) {
    public PublishedRubric {
        Checks.required(id); Checks.required(version); title = Checks.text(title);
        criteria = List.copyOf(Checks.required(criteria));
        Checks.require(!criteria.isEmpty(), "rubric must contain criteria");
        Checks.require(criteria.stream().map(RubricCriterion::id).distinct().count() == criteria.size(),
                "criterion identifiers must be unique");
        var total = criteria.stream().map(c -> c.weight().value()).reduce(BigDecimal.ZERO, BigDecimal::add);
        Checks.require(total.compareTo(new BigDecimal("100")) == 0, "criterion weights must sum to 100");
    }
}
