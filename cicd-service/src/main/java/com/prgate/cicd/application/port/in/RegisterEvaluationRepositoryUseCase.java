package com.prgate.cicd.application.port.in;

import com.prgate.cicd.domain.model.aggregates.EvaluationRepository;
import com.prgate.cicd.domain.model.valueobject.AcademicProjectId;
import com.prgate.cicd.domain.model.valueobject.GitHubRepository;

/** Internal technical enrollment. Academic project ownership and membership belong to Projects. */
public interface RegisterEvaluationRepositoryUseCase {
    EvaluationRepository register(AcademicProjectId projectId, GitHubRepository repository, String analysisBranch);
}
