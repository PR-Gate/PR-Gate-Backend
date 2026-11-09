package com.prgate.projects.domain;

import com.prgate.projects.domain.model.valueobject.GitHubOrganization;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class GitHubOrganizationTest {

    @Test
    void preservesExternalIdAndNormalizesLogin() {
        GitHubOrganization organization = new GitHubOrganization(123456789L, "  PRGate-Team  ");

        assertThat(organization.id()).isEqualTo(123456789L);
        assertThat(organization.login()).isEqualTo("prgate-team");
    }

    @Test
    void comparesNormalizedOrganizationMetadataByValue() {
        assertThat(new GitHubOrganization(123L, "PRGate-Team"))
                .isEqualTo(new GitHubOrganization(123L, "prgate-team"));
        assertThat(new GitHubOrganization(123L, "prgate-team"))
                .isNotEqualTo(new GitHubOrganization(456L, "prgate-team"));
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1, Long.MIN_VALUE})
    void rejectsNonPositiveId(long id) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new GitHubOrganization(id, "prgate-team"))
                .withMessage("GitHub organization id must be positive");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n", "\u2003"})
    void rejectsMissingLogin(String login) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new GitHubOrganization(123L, login))
                .withMessage("GitHub organization login is required");
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://github.com/prgate-team", "prgate/team", "prgate team", "@prgate-team", "prgate?x=1"})
    void rejectsUrlPathOrOtherNonLoginInput(String login) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new GitHubOrganization(123L, login))
                .withMessage("GitHub organization login must contain only letters, digits or hyphens");
    }
}
