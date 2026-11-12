package com.prgate.metrics.domain.model.valueobjects;
import java.time.Instant;
import java.util.*;
/** Trusted adapter supplies the IDs of the exact checks validated against the catalog. */
public record CatalogValidation(UUID planId, String catalogVersion, Set<CheckId> checkIds, Instant validatedAt) {
    public CatalogValidation {
        Checks.required(planId); catalogVersion = Checks.text(catalogVersion);
        checkIds = Set.copyOf(Checks.required(checkIds)); Checks.required(validatedAt);
    }
}
