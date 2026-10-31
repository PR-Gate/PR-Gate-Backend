package com.prgate.ai.domain.model.aggregates;

import java.util.*;
import com.prgate.ai.domain.model.enums.*;
import com.prgate.ai.domain.model.valueobjects.*;
import com.prgate.ai.domain.policy.TechnicalCheckCatalog;
import static com.prgate.ai.domain.model.valueobjects.Checks.*;

public final class TechnicalAssessmentPlan {
    private final PlanId id;
    private final RubricReference rubric;
    private final List<RubricCriterion> criteria;
    private final List<TechnicalCheck> checks;
    private PlanStatus status = PlanStatus.DRAFT;
    private String catalogVersion;

    public TechnicalAssessmentPlan(PlanId id, RubricReference rubric, List<RubricCriterion> criteria,
            List<TechnicalCheck> checks) {
        this.id = present(id);
        this.rubric = present(rubric);
        this.criteria = List.copyOf(present(criteria));
        this.checks = List.copyOf(present(checks));
        require(!this.criteria.isEmpty(), "plan requires criteria");
        Set<CriterionId> ids = new HashSet<>();
        this.criteria.forEach(c -> require(ids.add(c.id()), "duplicate criterion"));
        require(new HashSet<>(this.checks).size() == this.checks.size(), "duplicate check");
        this.checks.forEach(c -> require(ids.contains(c.criterionId()), "unknown check criterion"));
        require(this.criteria.stream()
                .allMatch(c -> this.checks.stream().anyMatch(k -> k.criterionId().equals(c.id()))),
                "every criterion requires a check or explicit manual review");
    }

    public void approve(TechnicalCheckCatalog catalog) {
        require(status == PlanStatus.DRAFT, "only draft plans can be approved");
        present(catalog);
        String version = text(catalog.version());
        checks.stream().filter(c -> c.mechanism() != CheckMechanism.MANUAL_REVIEW)
                .forEach(c -> require(catalog.supports(c), "unsupported technical check: " + c.target()));
        catalogVersion = version;
        status = PlanStatus.APPROVED;
    }

    public PlanId id() {
        return id;
    }

    public RubricReference rubric() {
        return rubric;
    }

    public List<RubricCriterion> criteria() {
        return criteria;
    }

    public List<TechnicalCheck> checks() {
        return checks;
    }

    public PlanStatus status() {
        return status;
    }

    public Optional<String> catalogVersion() {
        return Optional.ofNullable(catalogVersion);
    }
}