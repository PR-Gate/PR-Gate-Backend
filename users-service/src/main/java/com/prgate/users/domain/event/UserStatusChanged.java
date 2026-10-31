package com.prgate.users.domain.event;

import com.prgate.users.domain.model.UserStatus;
import java.time.Instant;
import java.util.UUID;

public record UserStatusChanged(
        UUID userId,
        UserStatus previousStatus,
        UserStatus newStatus,
        Instant occurredOn
) implements DomainEvent {
}
