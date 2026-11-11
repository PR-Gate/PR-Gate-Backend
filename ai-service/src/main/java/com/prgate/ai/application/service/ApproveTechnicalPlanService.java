package com.prgate.ai.application.service;

import com.prgate.ai.application.dto.*;
import com.prgate.ai.application.exception.TechnicalPlanNotFoundException;
import com.prgate.ai.application.port.in.ApproveTechnicalPlanUseCase;
import com.prgate.ai.application.port.out.AnalysisCatalogPort;
import com.prgate.ai.domain.model.enums.PlanStatus;
import com.prgate.ai.domain.repository.TechnicalAssessmentPlanRepository;
import static com.prgate.ai.domain.model.valueobjects.Checks.*;

public final class ApproveTechnicalPlanService implements ApproveTechnicalPlanUseCase {
    private final TechnicalAssessmentPlanRepository plans;
    private final AnalysisCatalogPort catalogs;

    public ApproveTechnicalPlanService(TechnicalAssessmentPlanRepository plans, AnalysisCatalogPort catalogs) {
        this.plans = present(plans);
        this.catalogs = present(catalogs);
    }

    @Override
    public TechnicalPlanResult approve(ApproveTechnicalPlanCommand command) {
        present(command);
        var plan = plans.findById(command.planId())
                .orElseThrow(() -> new TechnicalPlanNotFoundException(command.planId()));
        require(plan.status() == PlanStatus.DRAFT, "only draft plans can be approved");
        var catalog = present(catalogs.load(plan.language()));
        plan.approve(catalog);
        plans.save(plan);
        return TechnicalPlanResult.from(plan);
    }
}
