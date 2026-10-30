package com.prgate.cicd.domain.model.valueobject;

import java.util.Objects;

public record SonarProjectKey(String value) {
    public SonarProjectKey {
        Objects.requireNonNull(value, "Sonar project key is required");
        if (value.length() > 255 || !value.matches("[A-Za-z0-9_.:-]+") || value.matches("[0-9]+")) {
            throw new IllegalArgumentException("Invalid Sonar project key");
        }
    }

    /** Independent of repository names, which may change on GitHub. */
    public static SonarProjectKey forRepository(EvaluationRepositoryId id) {
        return new SonarProjectKey("prgate:" + Objects.requireNonNull(id).value());
    }
}
