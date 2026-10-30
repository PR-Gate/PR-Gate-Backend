package com.prgate.cicd.infrastructure.persistence.jpa.repository;

import com.prgate.cicd.infrastructure.persistence.jpa.entity.PipelineRunEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SpringDataPipelineRunRepository extends JpaRepository<PipelineRunEntity, UUID> {

    Optional<PipelineRunEntity> findByGithubRepositoryIdAndGithubRunIdAndGithubRunAttempt(
            long githubRepositoryId, long githubRunId, int githubRunAttempt);
}
