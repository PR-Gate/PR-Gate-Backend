package com.prgate.ai.application.dto;

import com.prgate.ai.domain.model.valueobjects.*;
import java.util.List;
import java.util.HashSet;
import static com.prgate.ai.domain.model.valueobjects.Checks.*;

public record FeedbackRequest(List<RubricCriterion> criteria, List<TechnicalFinding> findings) {
    public FeedbackRequest {
        criteria = List.copyOf(present(criteria));
        findings = List.copyOf(present(findings));
        var criterionIds = new HashSet<CriterionId>();
        criteria.forEach(c -> require(criterionIds.add(c.id()), "duplicate criterion"));
        var findingIds = new HashSet<FindingId>();
        findings.forEach(f -> {
            require(findingIds.add(f.id()), "duplicate finding");
            require(criterionIds.contains(f.criterionId()), "unknown finding criterion");
        });
    }
}
