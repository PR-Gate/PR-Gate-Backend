package com.prgate.users.domain.event;

import java.time.Instant;

public interface DomainEvent {
    Instant occurredOn();
}
