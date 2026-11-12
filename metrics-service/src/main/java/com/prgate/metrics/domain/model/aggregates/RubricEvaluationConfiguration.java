package com.prgate.metrics.domain.model.aggregates;
import com.prgate.metrics.domain.model.valueobjects.*;
import java.util.*;
public final class RubricEvaluationConfiguration {
    private final ConfigurationId id;
    private final PublishedRubric rubric;
    private final TechnicalPlanReference plan;
    private final List<EvaluationCheck> checks;
    private ApprovedConfiguration approved;
    public RubricEvaluationConfiguration(ConfigurationId id, PublishedRubric rubric,
            TechnicalPlanReference plan, List<EvaluationCheck> checks) {
        this.id = Checks.required(id); this.rubric = Checks.required(rubric);
        this.plan = Checks.required(plan); this.checks = List.copyOf(Checks.required(checks));
    }
    public ApprovedConfiguration approve(TeacherApproval approval, Optional<CatalogValidation> validation) {
        Checks.require(approved == null, "configuration already approved");
        var snapshot = new ApprovedConfiguration(id, rubric, plan, checks, approval, validation);
        approved = snapshot;
        return snapshot;
    }
    public Optional<ApprovedConfiguration> approved() { return Optional.ofNullable(approved); }
    public ConfigurationId id() { return id; }
}
