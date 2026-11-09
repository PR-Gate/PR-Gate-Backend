package com.prgate.projects.infrastructure.persistence.mapper;

import com.prgate.projects.domain.model.aggregates.CourseEnrollment;
import com.prgate.projects.domain.model.valueobject.CourseEnrollmentId;
import com.prgate.projects.domain.model.valueobject.CourseId;
import com.prgate.projects.domain.model.valueobject.StudentId;
import com.prgate.projects.infrastructure.persistence.entity.CourseEnrollmentJpaEntity;

public final class CourseEnrollmentPersistenceMapper {

    private CourseEnrollmentPersistenceMapper() {
    }

    public static CourseEnrollment toDomain(CourseEnrollmentJpaEntity entity) {
        return CourseEnrollment.reconstitute(new CourseEnrollmentId(entity.getId()), new CourseId(entity.getCourseId()),
                new StudentId(entity.getStudentId()), entity.getRequestedAt(), entity.getStatus(), entity.getResolvedAt());
    }
}
