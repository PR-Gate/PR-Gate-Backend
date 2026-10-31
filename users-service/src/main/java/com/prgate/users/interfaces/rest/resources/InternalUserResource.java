package com.prgate.users.interfaces.rest.resources;

import java.util.UUID;

public record InternalUserResource(
        UUID id,
        String firstName,
        String lastName,
        String role,
        String status) {
}
