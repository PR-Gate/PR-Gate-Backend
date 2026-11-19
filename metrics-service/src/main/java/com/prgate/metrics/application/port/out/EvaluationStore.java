package com.prgate.metrics.application.port.out;

import com.prgate.metrics.application.dto.*;
import com.prgate.metrics.domain.model.valueobjects.*;
import java.math.BigDecimal;
import java.util.*;

public interface EvaluationStore {
    void saveRubric(UUID projectId, PublishedRubric rubric, UUID actorId);
    void lockCourseRubrics(UUID courseId);
    boolean archiveCourseRubric(UUID courseId, UUID rubricId);
    Optional<PublishedRubric> rubric(UUID projectId, UUID rubricId, int version);
    List<PublishedRubric> rubrics(UUID projectId,int page,int size);
    List<StoredConfiguration> configurations(UUID projectId,long repositoryId);
    List<RepositoryChoice> repositories(String organization);
    List<AnalysisCompletedEvent> analyses(String organization,long repositoryId,String gitRef,int page,int size);
    int latestRubricVersion(UUID projectId, UUID rubricId);
    void activateConfiguration(StoredConfiguration configuration);
    Optional<StoredConfiguration> configuration(UUID id);
    Map<String, Object> gateStatus(UUID configurationId);
    void retryGate(UUID configurationId);
    Optional<AnalysisCompletedEvent> analysis(UUID eventId);
    Evidence evidence(UUID eventId);
    void retryEvidence(UUID eventId);
    Optional<StoredEvaluation> evaluation(UUID id);
    List<StoredEvaluation> evaluations(UUID projectId, long repositoryId, int page, int size);
    List<PendingEvaluation> pendingEvaluations();
    StoredEvaluation evaluateLocked(StoredConfiguration configuration, AnalysisCompletedEvent event,
            java.util.function.Function<List<ManualDecision>, EvaluationSummary> calculate, Evidence evidence);
    StoredEvaluation reviewLocked(UUID evaluationId,
            java.util.function.Function<StoredEvaluation, StoredEvaluation> review);

    record Evidence(String state, Map<String, BigDecimal> measures) { }
    record RepositoryChoice(long id,String fullName,String gitRef,java.time.Instant lastAnalyzedAt) { }
    record PendingEvaluation(UUID configurationId, UUID eventId) { }
    record StoredEvaluation(UUID id, UUID projectId, UUID configurationId, UUID eventId,
            long repositoryId, String gitRef, String commitSha, String status,
            EvaluationSummary summary, List<ManualDecision> decisions, String evidenceState) { }
}
