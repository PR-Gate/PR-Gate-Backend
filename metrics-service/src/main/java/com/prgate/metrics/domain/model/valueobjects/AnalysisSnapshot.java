package com.prgate.metrics.domain.model.valueobjects;
import java.time.Instant;
import java.util.*;
/** Only a successfully completed, correlated analysis can be represented here.
 * The adapter must verify that its collected observations belong to this exact analysis.
 */
public record AnalysisSnapshot(SonarAnalysisReference reference, Instant analyzedAt,
                               List<CheckObservation> observations) {
    public AnalysisSnapshot {
        Checks.required(reference); Checks.required(analyzedAt);
        observations = List.copyOf(Checks.required(observations));
        Checks.require(observations.stream().map(CheckObservation::checkId).distinct().count() == observations.size(),
                "duplicate observation for a check");
    }
}
