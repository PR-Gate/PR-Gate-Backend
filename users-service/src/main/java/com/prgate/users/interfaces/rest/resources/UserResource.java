package com.prgate.users.interfaces.rest.resources;

import java.time.Instant;
import java.util.UUID;

public record UserResource(
        UUID id,
        String cognitoSub,
        String firstName,
        String lastName,
        String contactEmail,
        String role,
        String status,
        Instant createdAt,
        Instant updatedAt) {
}
