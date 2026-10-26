package com.prgate.projects.application.security;

import com.prgate.projects.application.exception.AccessDeniedException;
import com.prgate.projects.application.exception.CourseNotFoundException;
import com.prgate.projects.application.port.out.CourseRepository;
import com.prgate.projects.domain.model.valueobject.CourseId;
import com.prgate.projects.domain.model.valueobject.TeacherId;
import java.util.Objects;

public final class ResponsibleTeacherPolicy {
    private final CourseRepository courses;

    public ResponsibleTeacherPolicy(CourseRepository courses) {
        this.courses = Objects.requireNonNull(courses);
    }

    public void requireResponsible(CourseId courseId, TeacherId teacherId) {
        var course = courses.findById(courseId)
                .orElseThrow(() -> new CourseNotFoundException("Course does not exist"));
        if (!course.getTeacherId().equals(teacherId)) {
            throw new AccessDeniedException("Only the responsible teacher can manage this group");
        }
    }
}
