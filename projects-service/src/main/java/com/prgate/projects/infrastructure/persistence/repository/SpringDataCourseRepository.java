package com.prgate.projects.infrastructure.persistence.repository;

import com.prgate.projects.infrastructure.persistence.entity.CourseJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SpringDataCourseRepository extends JpaRepository<CourseJpaEntity, UUID> {
}
