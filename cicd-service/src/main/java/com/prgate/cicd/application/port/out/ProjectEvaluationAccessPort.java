package com.prgate.cicd.application.port.out;

import com.prgate.cicd.domain.model.valueobject.AcademicProjectId;
import java.util.UUID;

/** Projects owns membership and teacher permissions. A user id alone grants no access. */
public interface ProjectEvaluationAccessPort {
    boolean mayReadEvaluations(UUID authenticatedUserId, AcademicProjectId projectId);
}
