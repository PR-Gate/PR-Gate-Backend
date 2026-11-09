package com.prgate.projects.domain.model.aggregates;

import com.prgate.projects.domain.model.valueobject.CourseId;
import com.prgate.projects.domain.model.valueobject.TeacherId;

import java.time.Instant;

/** Aggregate root representing a course section in an academic period. */
public final class Course {

    private final CourseId id;
    private final String name;
    private final String section;
    private final String academicPeriod;
    private final TeacherId teacherId;
    private final Instant createdAt;

    private Course(CourseId id, String name, String section, String academicPeriod,
                   TeacherId teacherId, Instant createdAt) {
        if (id == null) {
            throw new IllegalArgumentException("Course id is required");
        }
        if (teacherId == null) {
            throw new IllegalArgumentException("Teacher id is required");
        }
        if (createdAt == null) {
            throw new IllegalArgumentException("Creation time is required");
        }
        this.id = id;
        this.name = requiredText(name, "Course name", 150);
        this.section = requiredText(section, "Section", 50);
        this.academicPeriod = requiredText(academicPeriod, "Academic period", 30);
        this.teacherId = teacherId;
        this.createdAt = createdAt;
    }

    public static Course create(CourseId id, String name, String section, String academicPeriod,
                                TeacherId teacherId, Instant createdAt) {
        return new Course(id, name, section, academicPeriod, teacherId, createdAt);
    }

    public CourseId getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getSection() {
        return section;
    }

    public String getAcademicPeriod() {
        return academicPeriod;
    }

    public TeacherId getTeacherId() {
        return teacherId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    private static String requiredText(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        String normalized = value.strip();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(field + " must not exceed " + maxLength + " characters");
        }
        return normalized;
    }
}
