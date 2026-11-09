package com.prgate.cicd.application.port.out;

import com.prgate.cicd.domain.model.aggregates.EvaluationRepository;
import java.util.Optional;

public interface EvaluationRepositoryLookupPort {
    Optional<EvaluationRepository> findByGithubRepositoryId(long githubRepositoryId);
}
