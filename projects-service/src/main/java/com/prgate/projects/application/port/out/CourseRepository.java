package com.prgate.projects.application.port.out;

import com.prgate.projects.domain.model.aggregates.Course;

public interface CourseRepository {

    /** Completes only after persistence succeeds; failures must propagate to the caller. */
    void save(Course course);
}
