package com.prgate.ai.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prgate.ai.application.dto.*;
import com.prgate.ai.application.port.out.*;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

@Service
public class EstimateGradeService {
    private final GradeEstimationGenerator generator;
    private final GradeEstimateStore store;
    private final ObjectMapper json;
    public EstimateGradeService(GradeEstimationGenerator generator,GradeEstimateStore store,ObjectMapper json) {
        this.generator=generator; this.store=store; this.json=json;
    }
    public GradeEstimateResult estimate(GradeEstimateRequest request) {
        Objects.requireNonNull(request);
        String hash;
        try { hash=HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                .digest(json.writeValueAsBytes(request))); }
        catch (Exception exception) { throw new IllegalArgumentException("Invalid estimation request"); }
        var previous=store.find(hash);
        if (previous.isPresent()) return previous.get();
        var generated=generator.estimate(request);
        if (generated==null || generated.summary()==null || generated.summary().isBlank() || generated.criteria()==null
                || generated.criteria().size()!=request.criteria().size()) throw invalid();
        Map<UUID,GradeEstimateRequest.CriterionEvidence> expected=new HashMap<>();
        request.criteria().forEach(c->expected.put(c.criterionId(),c));
        Set<UUID> accepted=new HashSet<>();
        BigDecimal subtotal=BigDecimal.ZERO, covered=BigDecimal.ZERO;
        for (var item:generated.criteria()) {
            var criterion=expected.get(item.criterionId());
            if (criterion==null || !accepted.add(item.criterionId()) || item.justification()==null
                    || item.justification().isBlank() || item.supportingCheckIds()==null) throw invalid();
            Set<UUID> evidence=criterion.checks().stream().map(GradeEstimateRequest.CheckEvidence::id)
                    .collect(java.util.stream.Collectors.toSet());
            if (!evidence.containsAll(item.supportingCheckIds())) throw invalid();
            var points=item.estimatedPoints();
            if (points!=null) {
                if (!criterion.evidenceComplete() || item.supportingCheckIds().isEmpty() || points.signum()<0
                        || points.compareTo(criterion.maxPoints())>0 || points.stripTrailingZeros().scale()>2) throw invalid();
                subtotal=subtotal.add(points); covered=covered.add(criterion.maxPoints());
            }
        }
        boolean complete=covered.compareTo(BigDecimal.valueOf(20))==0;
        var result=new GradeEstimateResult(UUID.randomUUID(),request.evaluationId(),request.configurationId(),request.commitSha(),
                request.rubricId(),request.rubricVersion(),complete?"COMPLETE":"PARTIAL",false,BigDecimal.valueOf(20),
                complete?subtotal:null,subtotal,covered,generated.summary(),List.copyOf(generated.criteria()),
                generated.provider(),generated.model(),Instant.now());
        return store.saveFirst(hash,result);
    }
    private FeedbackGenerationException invalid() { return new FeedbackGenerationException("Invalid AI grade estimate evidence or point allocation"); }
}
