package com.prgate.metrics.application.port.out;
import java.util.UUID;

public interface ProjectAccessPort {
    Access check(UUID projectId, UUID actorId);
    record Access(boolean mayRead, boolean mayManage, String organization) { }
}
