package com.prgate.cicd.infrastructure.persistence.jpa.adapter;

import com.prgate.cicd.application.port.out.PipelineRunRepository;
import com.prgate.cicd.domain.model.aggregates.PipelineRun;
import com.prgate.cicd.domain.model.valueobject.GitHubRepository;
import com.prgate.cicd.domain.model.valueobject.GitHubRunAttempt;
import com.prgate.cicd.domain.model.valueobject.GitHubWorkflowRunId;
import com.prgate.cicd.infrastructure.persistence.jpa.mapper.PipelineRunEntityMapper;
import com.prgate.cicd.infrastructure.persistence.jpa.repository.SpringDataPipelineRunRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class PipelineRunPersistenceAdapter implements PipelineRunRepository {

    private final SpringDataPipelineRunRepository repository;
    private final PipelineRunEntityMapper mapper;

    public PipelineRunPersistenceAdapter(SpringDataPipelineRunRepository repository,
                                         PipelineRunEntityMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public Optional<PipelineRun> findByGitHubIdentity(GitHubRepository repository,
                                                      GitHubWorkflowRunId runId,
                                                      GitHubRunAttempt attempt) {
        return this.repository.findByGithubRepositoryIdAndGithubRunIdAndGithubRunAttempt(
                        repository.id(), runId.value(), attempt.value())
                .map(mapper::toDomain);
    }

    @Override
    public PipelineRun save(PipelineRun pipelineRun) {
        return mapper.toDomain(repository.save(mapper.toEntity(pipelineRun)));
    }
}
