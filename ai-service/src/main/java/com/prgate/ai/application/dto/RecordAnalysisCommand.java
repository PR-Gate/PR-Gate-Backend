package com.prgate.ai.application.dto;

import java.util.List;
import com.prgate.ai.domain.model.valueobjects.*;
import static com.prgate.ai.domain.model.valueobjects.Checks.*;

public record RecordAnalysisCommand(PlanId planId, SubmissionReference submission,
                                     String analysisId, List<TechnicalFinding> findings) {
    public RecordAnalysisCommand {
        present(planId);
        present(submission);
        analysisId = text(analysisId);
        findings = List.copyOf(present(findings));
    }
}
