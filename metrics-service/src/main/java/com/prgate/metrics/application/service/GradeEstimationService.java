package com.prgate.metrics.application.service;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.prgate.metrics.application.dto.GradeEstimationEvidence;
import com.prgate.metrics.application.port.out.*;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
public class GradeEstimationService {
    private final RubricEvaluationService evaluations;
    private final ProjectAccessPort access;
    private final GradeEstimatorPort estimator;
    private final GradeEstimateProjection estimates;
    private final ObjectMapper json;
    public GradeEstimationService(RubricEvaluationService evaluations,ProjectAccessPort access,GradeEstimatorPort estimator,
                                   GradeEstimateProjection estimates,ObjectMapper json) {
        this.evaluations=evaluations; this.access=access; this.estimator=estimator; this.estimates=estimates; this.json=json;
    }
    public String generate(UUID actor,UUID project,UUID evaluationId) {
        var rights=access.check(project,actor);
        if (!rights.mayRead() || !rights.mayManage()) throw new EvaluationAccessDenied();
        return generateForEvidence(evaluations.estimationEvidence(actor,project,evaluationId));
    }

    public void generateForWorker(UUID evaluationId) {
        generateForEvidence(evaluations.estimationEvidenceForWorker(evaluationId));
    }

    private String generateForEvidence(GradeEstimationEvidence evidence) {
        String hash=hash(evidence);
        return estimates.find(hash).orElseGet(()->{
            String result=estimator.estimate(evidence);
            estimates.save(hash,result);
            return result;
        });
    }
    public String get(UUID actor,UUID project,UUID evaluationId) {
        var evidence=evaluations.estimationEvidence(actor,project,evaluationId);
        return estimates.find(hash(evidence)).orElseThrow(NoSuchElementException::new);
    }
    private String hash(GradeEstimationEvidence evidence) {
        try { return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                .digest(json.writeValueAsBytes(evidence))); }
        catch (Exception exception) { throw new IllegalArgumentException("Invalid estimation evidence"); }
    }
}
