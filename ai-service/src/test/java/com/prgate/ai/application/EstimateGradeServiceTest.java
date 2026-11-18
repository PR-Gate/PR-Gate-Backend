package com.prgate.ai.application;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.prgate.ai.application.dto.*;
import com.prgate.ai.application.port.out.*;
import com.prgate.ai.application.service.EstimateGradeService;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EstimateGradeServiceTest {
    private final GradeEstimationGenerator generator=mock(GradeEstimationGenerator.class);
    private final GradeEstimateStore store=mock(GradeEstimateStore.class);
    private final EstimateGradeService service=new EstimateGradeService(generator,store,new ObjectMapper().findAndRegisterModules());
    @Test void estimatesCriterionPointsWithinTwentyAndNeverReturnsAnOfficialGrade() {
        var request=request(true);
        when(store.find(anyString())).thenReturn(Optional.empty());
        when(store.saveFirst(anyString(),any())).thenAnswer(i->i.getArgument(1));
        when(generator.estimate(request)).thenReturn(generation(request,new BigDecimal("17.5")));
        var result=service.estimate(request);
        assertEquals(new BigDecimal("17.5"),result.estimatedScore());
        assertFalse(result.officialGrade()); assertEquals("COMPLETE",result.status());
        assertEquals(0,result.maxScore().compareTo(BigDecimal.valueOf(20)));
    }
    @Test void missingEvidenceCannotReceiveInventedPoints() {
        var request=request(false);
        when(generator.estimate(request)).thenReturn(generation(request,BigDecimal.TEN));
        assertThrows(FeedbackGenerationException.class,()->service.estimate(request));
        verify(store,never()).saveFirst(anyString(),any());
    }
    @Test void partialEstimateDoesNotNormalizeMissingEvidenceToTwenty() {
        var request=request(false);
        when(store.saveFirst(anyString(),any())).thenAnswer(i->i.getArgument(1));
        when(generator.estimate(request)).thenReturn(generation(request,null));
        var result=service.estimate(request);
        assertNull(result.estimatedScore()); assertEquals("PARTIAL",result.status());
        assertEquals(BigDecimal.ZERO,result.coveredMaxPoints());
    }
    @Test void excessivePointsOrForeignEvidenceAreRejected() {
        var request=request(true);
        when(generator.estimate(request)).thenReturn(generation(request,new BigDecimal("20.01")));
        assertThrows(FeedbackGenerationException.class,()->service.estimate(request));
        when(generator.estimate(request)).thenReturn(new GradeEstimationGenerator.Generation("Estimate",List.of(
                new GradeEstimateResult.CriterionEstimate(request.criteria().getFirst().criterionId(),BigDecimal.TEN,
                        "Reason",List.of(UUID.randomUUID()))),"fake","test"));
        assertThrows(FeedbackGenerationException.class,()->service.estimate(request));
    }
    @Test void cachedIdenticalEvidenceDoesNotCallProviderAgain() {
        var request=request(true);
        var cached=mock(GradeEstimateResult.class);
        when(store.find(anyString())).thenReturn(Optional.of(cached));
        assertSame(cached,service.estimate(request)); verifyNoInteractions(generator);
    }
    private GradeEstimationGenerator.Generation generation(GradeEstimateRequest request,BigDecimal points) {
        var criterion=request.criteria().getFirst();
        return new GradeEstimationGenerator.Generation("Approximate, not official",List.of(
                new GradeEstimateResult.CriterionEstimate(criterion.criterionId(),points,"Based on supplied evidence",
                        points==null?List.of():List.of(criterion.checks().getFirst().id()))),"fake","test");
    }
    private GradeEstimateRequest request(boolean complete) {
        return new GradeEstimateRequest(UUID.randomUUID(),UUID.randomUUID(),"a".repeat(40),UUID.randomUUID(),1,
                List.of(new GradeEstimateRequest.CriterionEvidence(UUID.randomUUID(),"Tests",BigDecimal.valueOf(20),complete,
                        List.of(new GradeEstimateRequest.CheckEvidence(UUID.randomUUID(),"METRIC",complete?"PASSED":"NOT_EVALUABLE",
                                "coverage",complete?BigDecimal.valueOf(90):null,"GTE",BigDecimal.valueOf(80),"Technical evidence")))));
    }
}
