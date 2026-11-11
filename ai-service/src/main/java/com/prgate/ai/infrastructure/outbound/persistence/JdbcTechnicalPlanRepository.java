package com.prgate.ai.infrastructure.outbound.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prgate.ai.domain.model.aggregates.TechnicalAssessmentPlan;
import com.prgate.ai.domain.model.enums.PlanStatus;
import com.prgate.ai.domain.model.valueobjects.*;
import com.prgate.ai.domain.repository.TechnicalAssessmentPlanRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.*;

@Repository
public class JdbcTechnicalPlanRepository extends JdbcAggregateRepository<TechnicalAssessmentPlan>
        implements TechnicalAssessmentPlanRepository {
    public JdbcTechnicalPlanRepository(JdbcTemplate jdbc, ObjectMapper mapper) { super(jdbc, mapper, "technical-plan"); }
    public Optional<TechnicalAssessmentPlan> findById(PlanId id) { return load(id.value().toString()); }
    public void save(TechnicalAssessmentPlan plan) { store(plan.id().value().toString(), plan); }

    protected String encode(TechnicalAssessmentPlan p) {
        return json(new Snapshot(p.id(), p.rubric(), p.language(), p.criteria(), p.checks(),
                p.status(), p.catalogVersion().orElse(null)));
    }
    protected TechnicalAssessmentPlan decode(String payload) {
        var p = parse(payload, Snapshot.class);
        return TechnicalAssessmentPlan.restore(p.id(), p.rubric(), p.language(), p.criteria(),
                p.checks(), p.status(), p.catalogVersion());
    }
    private record Snapshot(PlanId id, RubricReference rubric, SourceLanguage language,
                            List<RubricCriterion> criteria, List<TechnicalCheck> checks,
                            PlanStatus status, String catalogVersion) {}
}
