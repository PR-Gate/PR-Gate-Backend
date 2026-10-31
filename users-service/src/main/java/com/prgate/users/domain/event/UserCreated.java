package com.prgate.users.domain.event;

import com.prgate.users.domain.model.UserRole;
import com.prgate.users.domain.model.UserStatus;
import java.time.Instant;
import java.util.UUID;

public record UserCreated(
        UUID userId,
        String cognitoSub,
        String firstName,
        String lastName,
        String contactEmail,
        UserRole role,
        UserStatus status,
        Instant occurredOn
) implements DomainEvent {
}
