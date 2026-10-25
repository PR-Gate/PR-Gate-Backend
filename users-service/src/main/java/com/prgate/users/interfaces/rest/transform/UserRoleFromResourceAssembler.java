package com.prgate.users.interfaces.rest.transform;

import com.prgate.users.domain.exception.InvalidUserStateException;
import com.prgate.users.domain.model.UserRole;
import com.prgate.users.interfaces.rest.resources.ChangeUserRoleResource;

import java.util.Objects;

public final class UserRoleFromResourceAssembler {

    private UserRoleFromResourceAssembler() {
    }

    public static UserRole toRoleFromResource(ChangeUserRoleResource resource) {
        Objects.requireNonNull(resource, "resource must not be null");
        try {
            return UserRole.valueOf(resource.role());
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new InvalidUserStateException("invalid role: " + resource.role());
        }
    }
}
