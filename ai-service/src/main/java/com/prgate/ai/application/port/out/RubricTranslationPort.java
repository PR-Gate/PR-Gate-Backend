package com.prgate.ai.application.port.out;

import com.prgate.ai.application.dto.RubricTranslationRequest;
import com.prgate.ai.application.dto.RubricTranslationResult;

public interface RubricTranslationPort {
    /** Must report communication, rejection and incomplete generation as RubricTranslationException. */
    RubricTranslationResult translate(RubricTranslationRequest request);
}
