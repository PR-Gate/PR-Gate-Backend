package com.prgate.ai.bootstrap;

import com.prgate.ai.application.port.in.*;
import com.prgate.ai.application.port.out.*;
import com.prgate.ai.application.service.*;
import com.prgate.ai.domain.repository.*;
import org.springframework.context.annotation.*;

@Configuration
public class ApplicationConfiguration {
    @Bean TranslateRubricUseCase translateRubric(RubricTranslationPort generator, TechnicalAssessmentPlanRepository plans) {
        return new TranslateRubricService(generator, plans);
    }
    @Bean ApproveTechnicalPlanUseCase approveTechnicalPlan(TechnicalAssessmentPlanRepository plans, AnalysisCatalogPort catalog) {
        return new ApproveTechnicalPlanService(plans, catalog);
    }
    @Bean GetTechnicalPlanUseCase getTechnicalPlan(TechnicalAssessmentPlanRepository plans) {
        return new GetTechnicalPlanService(plans);
    }
    @Bean GenerateFeedbackUseCase generateFeedback(AssessmentRepository assessments,
            TechnicalAssessmentPlanRepository plans, FeedbackGenerator generator) {
        return new GenerateFeedbackService(assessments, plans, generator);
    }
    @Bean AssessmentUseCases assessmentUseCases(AssessmentRepository assessments,
            TechnicalAssessmentPlanRepository plans, GenerateFeedbackUseCase feedback) {
        return new AssessmentService(assessments, plans, feedback);
    }
}
