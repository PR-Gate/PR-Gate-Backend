package com.prgate.metrics.application.port.out;
import java.util.UUID;

public interface ProjectAccessPort {
    Access check(UUID projectId, UUID actorId);
    default Access checkCourse(UUID courseId, UUID actorId) { return new Access(false, false, null); }
    record Access(boolean mayRead, boolean mayManage, String organization, UUID courseId) {
        public Access(boolean mayRead, boolean mayManage, String organization) {
            this(mayRead, mayManage, organization, null);
        }
    }
}
