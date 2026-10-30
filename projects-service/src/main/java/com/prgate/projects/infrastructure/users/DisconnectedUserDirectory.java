package com.prgate.projects.infrastructure.users;

import com.prgate.projects.application.exception.UserDirectoryException;
import com.prgate.projects.application.model.UserAccount;
import com.prgate.projects.application.port.out.CognitoIdentityDirectory;
import com.prgate.projects.application.port.out.UserDirectory;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/** Keeps user-dependent operations unavailable until a local user projection is implemented. */
@Component
public class DisconnectedUserDirectory implements UserDirectory, CognitoIdentityDirectory {

    @Override
    public Optional<UserAccount> findById(UUID userId) {
        throw new UserDirectoryException("User integration is disabled; the local user projection is not available yet");
    }

    @Override
    public Optional<UserAccount> findByCognitoSub(String cognitoSub) {
        throw new UserDirectoryException("User integration is disabled; Cognito identity mapping is not available yet");
    }
}
