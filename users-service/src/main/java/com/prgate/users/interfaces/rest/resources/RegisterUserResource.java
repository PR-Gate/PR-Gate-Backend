package com.prgate.users.interfaces.rest.resources;

public record RegisterUserResource(
        String cognitoSub,
        String firstName,
        String lastName,
        String contactEmail) {
}
