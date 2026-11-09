package com.prgate.cicd.infrastructure.persistence.jpa.adapter;

import com.prgate.cicd.application.port.out.PipelineRunQueryPort;
import com.prgate.cicd.domain.model.aggregates.PipelineRun;
import com.prgate.cicd.infrastructure.persistence.jpa.mapper.PipelineRunEntityMapper;
import com.prgate.cicd.infrastructure.persistence.jpa.repository.SpringDataPipelineRunRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@Transactional(readOnly = true)
public class PipelineRunQueryAdapter implements PipelineRunQueryPort {
    private final SpringDataPipelineRunRepository repository;
    private final PipelineRunEntityMapper mapper;

    public PipelineRunQueryAdapter(SpringDataPipelineRunRepository repository, PipelineRunEntityMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public Optional<PipelineRun> findById(UUID id) {
        return repository.findById(id).map(mapper::toDomain);
    }

    @Override
    public List<PipelineRun> findByRepository(long repositoryId, String branch, int page, int size) {
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id"));
        var rows = branch == null
                ? repository.findByGithubRepositoryId(repositoryId, pageable)
                : repository.findByGithubRepositoryIdAndBranchName(repositoryId, branch, pageable);
        return rows.stream().map(mapper::toDomain).toList();
    }
}
