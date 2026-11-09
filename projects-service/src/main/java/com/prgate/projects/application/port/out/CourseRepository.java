package com.prgate.projects.application.port.out;

import com.prgate.projects.domain.model.aggregates.Course;
import com.prgate.projects.domain.model.valueobject.CourseId;

import java.util.Optional;

public interface CourseRepository {

    /** Completes only after persistence succeeds; failures must propagate to the caller. */
    void save(Course course);

    /** Empty means course not found; persistence failures must propagate. */
    Optional<Course> findById(CourseId courseId);
}
