package com.prgate.users.interfaces.rest.resources;

import java.time.Instant;

public record ApiErrorResource(
        int status,
        String error,
        String message,
        Instant timestamp) {
}
