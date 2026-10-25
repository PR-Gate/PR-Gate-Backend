package com.prgate.users.domain.event;

import com.prgate.users.domain.model.UserRole;
import java.time.Instant;
import java.util.UUID;

public record UserRoleChanged(
        UUID userId,
        UserRole previousRole,
        UserRole newRole,
        Instant occurredOn
) implements DomainEvent {
}
