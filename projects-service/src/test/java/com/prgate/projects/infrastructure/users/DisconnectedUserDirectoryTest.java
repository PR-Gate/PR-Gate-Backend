package com.prgate.projects.infrastructure.users;

import com.prgate.projects.application.exception.UserDirectoryException;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DisconnectedUserDirectoryTest {

    private final DisconnectedUserDirectory directory = new DisconnectedUserDirectory();

    @Test
    void disabledIntegrationDoesNotClaimUserIsMissingOrAuthorized() {
        assertThatThrownBy(() -> directory.findById(UUID.randomUUID()))
                .isInstanceOf(UserDirectoryException.class);
    }

    @Test
    void disabledIntegrationCannotResolveCognitoIdentity() {
        assertThatThrownBy(() -> directory.findByCognitoSub("subject-1"))
                .isInstanceOf(UserDirectoryException.class);
    }
}
