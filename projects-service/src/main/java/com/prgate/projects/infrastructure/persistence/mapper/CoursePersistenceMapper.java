package com.prgate.projects.infrastructure.persistence.mapper;

import com.prgate.projects.domain.model.aggregates.Course;
import com.prgate.projects.infrastructure.persistence.entity.CourseJpaEntity;

public final class CoursePersistenceMapper {

    private CoursePersistenceMapper() {
    }

    public static CourseJpaEntity toEntity(Course course) {
        return new CourseJpaEntity(course.getId().value(), course.getName(), course.getSection(),
                course.getAcademicPeriod(), course.getTeacherId().value(), course.getCreatedAt());
    }
}
