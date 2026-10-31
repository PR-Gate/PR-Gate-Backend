package com.prgate.projects.infrastructure.persistence.adapter;

import com.prgate.projects.application.port.out.ProjectRepository;
import com.prgate.projects.domain.model.aggregates.Project;
import com.prgate.projects.domain.model.valueobject.ProjectId;
import com.prgate.projects.infrastructure.persistence.mapper.ProjectPersistenceMapper;
import com.prgate.projects.infrastructure.persistence.repository.SpringDataProjectRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Repository
public class JpaProjectRepositoryAdapter implements ProjectRepository {

    private final SpringDataProjectRepository repository;

    public JpaProjectRepositoryAdapter(SpringDataProjectRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public void save(Project project) {
        repository.saveAndFlush(ProjectPersistenceMapper.toEntity(project));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Project> findById(ProjectId projectId) {
        return repository.findById(projectId.value()).map(ProjectPersistenceMapper::toDomain);
    }
}
