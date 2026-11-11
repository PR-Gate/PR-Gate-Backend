package com.prgate.ai.domain.repository;
import java.util.Optional;
import com.prgate.ai.domain.model.aggregates.TechnicalAssessmentPlan;
import com.prgate.ai.domain.model.valueobjects.PlanId;
/** Implementations must protect concurrent aggregate updates. */
public interface TechnicalAssessmentPlanRepository { Optional<TechnicalAssessmentPlan> findById(PlanId id); void save(TechnicalAssessmentPlan aggregate); }