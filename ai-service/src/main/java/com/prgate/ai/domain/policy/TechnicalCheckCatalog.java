package com.prgate.ai.domain.policy;
import com.prgate.ai.domain.model.valueobjects.TechnicalCheck;
/** Immutable catalog snapshot; supports validates target, language and parameter values. */
public interface TechnicalCheckCatalog { String version(); boolean supports(TechnicalCheck check); }