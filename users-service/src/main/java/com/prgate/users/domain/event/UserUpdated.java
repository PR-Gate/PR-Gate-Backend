package com.prgate.users.domain.event;

import java.time.Instant;
import java.util.UUID;

public record UserUpdated(
        UUID userId,
        String firstName,
        String lastName,
        String contactEmail,
        Instant occurredOn
) implements DomainEvent {
}
