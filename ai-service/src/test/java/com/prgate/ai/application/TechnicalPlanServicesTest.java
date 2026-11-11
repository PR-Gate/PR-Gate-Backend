package com.prgate.ai.application;

import com.prgate.ai.application.dto.*;
import com.prgate.ai.application.exception.*;
import com.prgate.ai.application.port.out.*;
import com.prgate.ai.application.service.*;
import com.prgate.ai.domain.exception.DomainRuleViolation;
import com.prgate.ai.domain.model.aggregates.TechnicalAssessmentPlan;
import com.prgate.ai.domain.model.enums.*;
import com.prgate.ai.domain.model.valueobjects.*;
import com.prgate.ai.domain.policy.TechnicalCheckCatalog;
import com.prgate.ai.domain.repository.TechnicalAssessmentPlanRepository;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TechnicalPlanServicesTest {
    private final TechnicalAssessmentPlanRepository plans = mock(TechnicalAssessmentPlanRepository.class);
    private final RubricTranslationPort translator = mock(RubricTranslationPort.class);
    private final AnalysisCatalogPort catalogs = mock(AnalysisCatalogPort.class);
    private final CriterionId criterion = CriterionId.create();
    private final RubricReference rubric = new RubricReference("rubric-1", 2);
    private final SourceLanguage language = new SourceLanguage("JAVA");
    private final List<RubricCriterion> criteria = List.of(
            new RubricCriterion(criterion, "Readable code", "Explain intent"));

    private TechnicalCheck check(CriterionId id) {
        return new TechnicalCheck(id, CheckMechanism.SONAR_RULE, "java:fixture", Map.of(), "Naming");
    }

    private TechnicalAssessmentPlan draft() {
        return new TechnicalAssessmentPlan(PlanId.create(), rubric, language, criteria, List.of(check(criterion)));
    }

    private TechnicalCheckCatalog catalog(boolean supported) {
        return new TechnicalCheckCatalog() {
            public String version() { return "catalog-v2"; }
            public boolean supports(TechnicalCheck check) { return supported; }
        };
    }

    @Test void translatesPreservingInputIdentityLanguageAndDraftStatus() {
        when(translator.translate(any())).thenReturn(new RubricTranslationResult(List.of(check(criterion))));
        var result = new TranslateRubricService(translator, plans)
                .translate(new TranslateRubricCommand(rubric, language, criteria));
        assertEquals(PlanStatus.DRAFT, result.status());
        assertEquals(rubric, result.rubric());
        assertEquals("java", result.language().value());
        assertEquals(criteria, result.criteria());
        assertTrue(result.catalogVersion().isEmpty());
        verify(translator).translate(new RubricTranslationRequest(rubric, language, criteria));
        verify(plans).save(argThat(p -> p.id().equals(result.id()) && p.status() == PlanStatus.DRAFT));
        assertThrows(UnsupportedOperationException.class, () -> result.checks().clear());
    }

    @Test void rejectsInvalidInputBeforeCallingGenerator() {
        assertThrows(DomainRuleViolation.class,
                () -> new TranslateRubricCommand(rubric, language, List.of()));
        assertThrows(DomainRuleViolation.class,
                () -> new TranslateRubricCommand(rubric, language, List.of(criteria.getFirst(), criteria.getFirst())));
        assertThrows(DomainRuleViolation.class, () -> new SourceLanguage(" "));
        verifyNoInteractions(translator, plans);
    }

    @Test void rejectsUnknownAndUncoveredCriteriaWithoutSaving() {
        var service = new TranslateRubricService(translator, plans);
        var command = new TranslateRubricCommand(rubric, language, criteria);
        when(translator.translate(any())).thenReturn(new RubricTranslationResult(List.of(check(CriterionId.create()))));
        assertThrows(InvalidTechnicalProposalException.class, () -> service.translate(command));
        when(translator.translate(any())).thenReturn(new RubricTranslationResult(List.of()));
        assertThrows(InvalidTechnicalProposalException.class, () -> service.translate(command));
        verifyNoInteractions(plans);
    }

    @Test void providerFailureOrMissingResultNeverCreatesPlan() {
        var service = new TranslateRubricService(translator, plans);
        var command = new TranslateRubricCommand(rubric, language, criteria);
        when(translator.translate(any())).thenThrow(new RubricTranslationException("Unavailable"));
        assertThrows(RubricTranslationException.class, () -> service.translate(command));
        doReturn(null).when(translator).translate(any());
        assertThrows(InvalidTechnicalProposalException.class, () -> service.translate(command));
        verifyNoInteractions(plans);
    }

    @Test void approvalUsesStoredLanguageAndPersistsCatalogVersion() {
        var plan = draft();
        when(plans.findById(plan.id())).thenReturn(Optional.of(plan));
        when(catalogs.load(language)).thenReturn(catalog(true));
        var result = new ApproveTechnicalPlanService(plans, catalogs)
                .approve(new ApproveTechnicalPlanCommand(plan.id()));
        assertEquals(PlanStatus.APPROVED, result.status());
        assertEquals("catalog-v2", result.catalogVersion().orElseThrow());
        verify(catalogs).load(language);
        verify(plans).save(plan);
    }

    @Test void unsupportedChecksLeavePlanDraftWithoutSaving() {
        var plan = draft();
        when(plans.findById(plan.id())).thenReturn(Optional.of(plan));
        when(catalogs.load(language)).thenReturn(catalog(false));
        assertThrows(DomainRuleViolation.class, () -> new ApproveTechnicalPlanService(plans, catalogs)
                .approve(new ApproveTechnicalPlanCommand(plan.id())));
        assertEquals(PlanStatus.DRAFT, plan.status());
        assertTrue(plan.catalogVersion().isEmpty());
        verify(plans, never()).save(any());
    }

    @Test void missingOrAlreadyApprovedPlanDoesNotLoadCatalogOrSave() {
        var missing = PlanId.create();
        when(plans.findById(missing)).thenReturn(Optional.empty());
        var service = new ApproveTechnicalPlanService(plans, catalogs);
        assertThrows(TechnicalPlanNotFoundException.class,
                () -> service.approve(new ApproveTechnicalPlanCommand(missing)));
        var approved = draft();
        approved.approve(catalog(true));
        when(plans.findById(approved.id())).thenReturn(Optional.of(approved));
        assertThrows(DomainRuleViolation.class,
                () -> service.approve(new ApproveTechnicalPlanCommand(approved.id())));
        verifyNoInteractions(catalogs);
        verify(plans, never()).save(any());
    }

    @Test void catalogFailureDoesNotApproveOrSave() {
        var plan = draft();
        when(plans.findById(plan.id())).thenReturn(Optional.of(plan));
        when(catalogs.load(language)).thenThrow(new IllegalStateException("Catalog unavailable"));
        assertThrows(IllegalStateException.class, () -> new ApproveTechnicalPlanService(plans, catalogs)
                .approve(new ApproveTechnicalPlanCommand(plan.id())));
        assertEquals(PlanStatus.DRAFT, plan.status());
        verify(plans, never()).save(any());
    }

    @Test void getsImmutableProjectionAndReportsMissingPlan() {
        var plan = draft();
        when(plans.findById(plan.id())).thenReturn(Optional.of(plan));
        var service = new GetTechnicalPlanService(plans);
        var result = service.get(plan.id());
        plan.approve(catalog(true));
        assertEquals(PlanStatus.DRAFT, result.status());
        var missing = PlanId.create();
        when(plans.findById(missing)).thenReturn(Optional.empty());
        assertThrows(TechnicalPlanNotFoundException.class, () -> service.get(missing));
        verify(plans, never()).save(any());
    }
}