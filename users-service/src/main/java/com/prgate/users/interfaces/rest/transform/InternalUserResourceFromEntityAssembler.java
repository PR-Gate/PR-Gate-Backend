package com.prgate.users.interfaces.rest.transform;

import com.prgate.users.domain.model.User;
import com.prgate.users.interfaces.rest.resources.InternalUserResource;

import java.util.Objects;

public final class InternalUserResourceFromEntityAssembler {

    private InternalUserResourceFromEntityAssembler() {
    }

    public static InternalUserResource toResourceFromEntity(User entity) {
        Objects.requireNonNull(entity, "entity must not be null");
        return new InternalUserResource(
                entity.getId(),
                entity.getFirstName(),
                entity.getLastName(),
                entity.getRole().name(),
                entity.getStatus().name());
    }
}
