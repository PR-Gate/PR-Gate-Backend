package com.prgate.users.interfaces.rest.transform;

import com.prgate.users.domain.exception.InvalidUserStateException;
import com.prgate.users.domain.model.UserStatus;
import com.prgate.users.interfaces.rest.resources.ChangeUserStatusResource;

import java.util.Objects;

public final class UserStatusFromResourceAssembler {

    private UserStatusFromResourceAssembler() {
    }

    public static UserStatus toStatusFromResource(ChangeUserStatusResource resource) {
        Objects.requireNonNull(resource, "resource must not be null");
        try {
            return UserStatus.valueOf(resource.status());
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new InvalidUserStateException("invalid status: " + resource.status());
        }
    }
}
