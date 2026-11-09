package com.prgate.projects.application.port.out;

import com.prgate.projects.application.model.UserAccount;

import java.util.Optional;
import java.util.UUID;

public interface UserDirectory {

    /** Empty means user not found; service and protocol failures must throw an exception. */
    Optional<UserAccount> findById(UUID userId);
}
