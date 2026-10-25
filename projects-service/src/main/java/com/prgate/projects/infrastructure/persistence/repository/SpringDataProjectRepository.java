package com.prgate.projects.infrastructure.persistence.repository;

import com.prgate.projects.infrastructure.persistence.entity.ProjectJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SpringDataProjectRepository extends JpaRepository<ProjectJpaEntity, UUID> {
}
