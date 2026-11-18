package com.prgate.metrics.application.service;
import java.util.Set;

/** Global-code metrics. New-code metrics are deliberately not aliases of these keys. */
public final class SupportedMetrics {
    public static final Set<String> KEYS = Set.of("coverage", "duplicated_lines_density", "ncloc",
            "bugs", "vulnerabilities", "code_smells", "sqale_index", "sqale_debt_ratio");
    private SupportedMetrics() { }
}
