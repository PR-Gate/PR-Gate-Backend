package com.prgate.users.interfaces.rest.transform;

import com.prgate.users.domain.model.User;
import com.prgate.users.interfaces.rest.resources.UserResource;

import java.util.List;
import java.util.Objects;

public final class UserResourceFromEntityAssembler {

    private UserResourceFromEntityAssembler() {
    }

    public static UserResource toResourceFromEntity(User entity) {
        Objects.requireNonNull(entity, "entity must not be null");
        return new UserResource(
                entity.getId(),
                entity.getCognitoSub(),
                entity.getFirstName(),
                entity.getLastName(),
                entity.getContactEmail(),
                entity.getRole().name(),
                entity.getStatus().name(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }

    public static List<UserResource> toResourcesFromEntities(List<User> entities) {
        Objects.requireNonNull(entities, "entities must not be null");
        return entities.stream()
                .map(UserResourceFromEntityAssembler::toResourceFromEntity)
                .toList();
    }
}
