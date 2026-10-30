package com.prgate.cicd.domain.model.valueobject;

import java.util.Objects;
import java.util.Locale;

public record GitCommitSha(String value) {

    public GitCommitSha {
        Objects.requireNonNull(value, "Commit SHA is required");
        value = value.strip().toLowerCase(Locale.ROOT);
        if (!value.matches("[a-f0-9]{40}|[a-f0-9]{64}")) {
            throw new IllegalArgumentException("Commit SHA must contain 40 or 64 hexadecimal characters");
        }
    }
}
