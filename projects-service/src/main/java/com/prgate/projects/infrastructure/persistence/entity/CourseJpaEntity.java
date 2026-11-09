package com.prgate.projects.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "courses")
public class CourseJpaEntity {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(nullable = false, length = 50)
    private String section;

    @Column(name = "academic_period", nullable = false, length = 30)
    private String academicPeriod;

    @Column(name = "teacher_id", nullable = false, updatable = false)
    private UUID teacherId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected CourseJpaEntity() {
    }

    public CourseJpaEntity(UUID id, String name, String section, String academicPeriod,
                           UUID teacherId, Instant createdAt) {
        this.id = id;
        this.name = name;
        this.section = section;
        this.academicPeriod = academicPeriod;
        this.teacherId = teacherId;
        this.createdAt = createdAt;
    }

    public UUID getId() {
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

    public UUID getTeacherId() {
        return teacherId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
