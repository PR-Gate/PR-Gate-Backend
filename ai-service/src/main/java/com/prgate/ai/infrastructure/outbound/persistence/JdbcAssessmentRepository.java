package com.prgate.ai.infrastructure.outbound.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prgate.ai.domain.model.aggregates.Assessment;
import com.prgate.ai.domain.model.enums.AssessmentStatus;
import com.prgate.ai.domain.model.valueobjects.*;
import com.prgate.ai.domain.repository.*;
import org.springframework.dao.DataRetrievalFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.*;

@Repository
public class JdbcAssessmentRepository extends JdbcAggregateRepository<Assessment> implements AssessmentRepository {
    private final TechnicalAssessmentPlanRepository plans;
    public JdbcAssessmentRepository(JdbcTemplate jdbc, ObjectMapper mapper, TechnicalAssessmentPlanRepository plans) {
        super(jdbc, mapper, "assessment");
        this.plans = plans;
    }
    public Optional<Assessment> findById(AssessmentId id) { return load(id.value().toString()); }
    public void save(Assessment a) { store(a.id().value().toString(), a); }

    protected String encode(Assessment a) {
        return json(new Snapshot(a.id(), a.planId(), a.submission(), a.status(), a.analysisId().orElse(null),
                a.findings(), a.summary().orElse(null), a.feedback(), a.failedAt().orElse(null),
                a.failureReason().orElse(null)));
    }
    protected Assessment decode(String payload) {
        var s = parse(payload, Snapshot.class);
        var plan = plans.findById(s.planId()).orElseThrow(() -> new DataRetrievalFailureException("Assessment plan missing"));
        var a = new Assessment(s.id(), s.submission(), plan);
        AssessmentStatus step = s.status() == AssessmentStatus.FAILED ? s.failedAt() : s.status();
        if (step == null) throw new DataRetrievalFailureException("Invalid assessment state");
        if (step != AssessmentStatus.PENDING) a.startAnalysis();
        if (step == AssessmentStatus.ANALYZED || step == AssessmentStatus.COMPLETED)
            a.completeAnalysis(s.analysisId(), s.submission(), s.findings());
        if (step == AssessmentStatus.COMPLETED) a.completeFeedback(s.summary(), s.feedback());
        if (s.status() == AssessmentStatus.FAILED) a.fail(s.failureReason());
        return a;
    }
    private record Snapshot(AssessmentId id, PlanId planId, SubmissionReference submission, AssessmentStatus status,
                            String analysisId, List<TechnicalFinding> findings, String summary,
                            List<FeedbackItem> feedback, AssessmentStatus failedAt, String failureReason) {}
}
