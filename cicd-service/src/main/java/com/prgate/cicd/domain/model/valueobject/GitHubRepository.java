package com.prgate.cicd.domain.model.valueobject;

import java.util.Objects;
import java.util.regex.Pattern;

/** Stable GitHub repository identity plus its name as observed for this run. */
public record GitHubRepository(long id, String owner, String name) {

    private static final Pattern COMPONENT = Pattern.compile("[A-Za-z0-9_.-]+");

    public GitHubRepository {
        if (id <= 0) {
            throw new IllegalArgumentException("GitHub repository id must be positive");
        }
        owner = normalizeComponent(owner, "Repository owner");
        name = normalizeComponent(name, "Repository name");
    }

    public String fullName() {
        return owner + "/" + name;
    }

    private static String normalizeComponent(String value, String label) {
        Objects.requireNonNull(value, label + " is required");
        String normalized = value.strip();
        if (normalized.isEmpty() || !COMPONENT.matcher(normalized).matches()) {
            throw new IllegalArgumentException(label + " has an invalid format");
        }
        return normalized;
    }
}
