package com.prgate.projects.application.port.out;

import com.prgate.projects.domain.model.valueobject.GitHubOrganization;
import java.util.Optional;

public interface GitHubOrganizationDirectory {
    /** Empty only for a missing organization. Transport or protocol failures must raise GitHubDirectoryException. */
    Optional<GitHubOrganization> findByLogin(String login);
}
