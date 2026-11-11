package com.prgate.ai.application.port.out;

import com.prgate.ai.application.dto.FeedbackRequest;
import com.prgate.ai.application.dto.FeedbackResult;

public interface FeedbackGenerator {
    FeedbackResult generate(FeedbackRequest request);
}
