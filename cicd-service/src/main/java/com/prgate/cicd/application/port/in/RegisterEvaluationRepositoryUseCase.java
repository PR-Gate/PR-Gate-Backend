package com.prgate.cicd.application.port.in;

import com.prgate.cicd.domain.model.aggregates.EvaluationRepository;
import com.prgate.cicd.domain.model.valueobject.AcademicProjectId;
import com.prgate.cicd.domain.model.valueobject.GitHubRepository;
import com.prgate.cicd.domain.model.valueobject.SonarProjectKey;

/** Internal technical enrollment. Academic project ownership and membership belong to Projects. */
public interface RegisterEvaluationRepositoryUseCase {
    EvaluationRepository register(AcademicProjectId projectId, GitHubRepository repository, String analysisBranch);
    EvaluationRepository registerExisting(AcademicProjectId projectId, GitHubRepository repository, String analysisBranch,
                                          SonarProjectKey sonarProjectKey);
}
