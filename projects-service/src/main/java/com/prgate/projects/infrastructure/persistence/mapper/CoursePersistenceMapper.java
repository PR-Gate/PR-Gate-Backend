package com.prgate.projects.infrastructure.persistence.mapper;

import com.prgate.projects.domain.model.aggregates.Course;
import com.prgate.projects.domain.model.valueobject.CourseId;
import com.prgate.projects.domain.model.valueobject.TeacherId;
import com.prgate.projects.infrastructure.persistence.entity.CourseJpaEntity;

public final class CoursePersistenceMapper {

    private CoursePersistenceMapper() {
    }

    public static CourseJpaEntity toEntity(Course course) {
        return new CourseJpaEntity(course.getId().value(), course.getName(), course.getSection(),
                course.getAcademicPeriod(), course.getTeacherId().value(), course.getCreatedAt());
    }

    public static Course toDomain(CourseJpaEntity entity) {
        return Course.create(new CourseId(entity.getId()), entity.getName(), entity.getSection(),
                entity.getAcademicPeriod(), new TeacherId(entity.getTeacherId()), entity.getCreatedAt());
    }
}
