package com.prgate.projects.domain.model.valueobject;

import java.util.Locale;

/** Metadata of an external GitHub organization; contains no credentials. */
public record GitHubOrganization(long id, String login) {

    public GitHubOrganization {
        if (id <= 0) {
            throw new IllegalArgumentException("GitHub organization id must be positive");
        }
        if (login == null || login.isBlank()) {
            throw new IllegalArgumentException("GitHub organization login is required");
        }
        login = login.strip().toLowerCase(Locale.ROOT);
        if (!login.matches("[a-z0-9-]+")) {
            throw new IllegalArgumentException("GitHub organization login must contain only letters, digits or hyphens");
        }
    }
}
