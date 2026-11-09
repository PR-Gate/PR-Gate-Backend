package com.prgate.cicd.domain.model.valueobject;

import com.prgate.cicd.domain.model.enums.QualityGateStatus;

import java.net.URI;
import java.time.Instant;
import java.util.Objects;

public record SonarAnalysisResult(String analysisId, QualityGateStatus qualityGateStatus,
                                 SonarMetrics metrics, URI dashboardUrl, Instant analyzedAt) {

    public SonarAnalysisResult {
        Objects.requireNonNull(analysisId, "SonarQube analysis id is required");
        analysisId = analysisId.strip();
        if (analysisId.isEmpty()) {
            throw new IllegalArgumentException("SonarQube analysis id must not be blank");
        }
        Objects.requireNonNull(qualityGateStatus, "Quality Gate status is required");
        Objects.requireNonNull(metrics, "SonarQube metrics are required");
        Objects.requireNonNull(dashboardUrl, "SonarQube dashboard URL is required");
        String scheme = dashboardUrl.getScheme();
        if (scheme == null || !(scheme.equalsIgnoreCase("https") || scheme.equalsIgnoreCase("http"))
                || dashboardUrl.getHost() == null) {
            throw new IllegalArgumentException("SonarQube dashboard URL must be a valid HTTP(S) URL");
        }
        Objects.requireNonNull(analyzedAt, "Analysis time is required");
    }
}
