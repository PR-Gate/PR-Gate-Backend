package com.prgate.users.interfaces.rest.resources;

public record UpdateUserProfileResource(
        String firstName,
        String lastName,
        String contactEmail) {
}
