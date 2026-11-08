package com.prgate.ai.application.port.out;

import com.prgate.ai.domain.model.valueobjects.SourceLanguage;
import com.prgate.ai.domain.policy.TechnicalCheckCatalog;

public interface AnalysisCatalogPort {
    /** Returns an immutable snapshot for this language, including supported parameter values. */
    TechnicalCheckCatalog load(SourceLanguage language);
}
