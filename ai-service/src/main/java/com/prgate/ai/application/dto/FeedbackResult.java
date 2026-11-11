package com.prgate.ai.application.dto;

import com.prgate.ai.domain.model.valueobjects.FeedbackItem;
import java.util.List;
import static com.prgate.ai.domain.model.valueobjects.Checks.*;

public record FeedbackResult(String summary, List<FeedbackItem> feedback) {
    public FeedbackResult {
        summary = text(summary);
        feedback = List.copyOf(present(feedback));
    }
}
