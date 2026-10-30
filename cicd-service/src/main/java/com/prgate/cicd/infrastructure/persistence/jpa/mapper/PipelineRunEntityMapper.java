package com.prgate.cicd.infrastructure.persistence.jpa.mapper;

import com.prgate.cicd.domain.model.aggregates.PipelineRun;
import com.prgate.cicd.domain.model.enums.PipelineConclusion;
import com.prgate.cicd.domain.model.enums.PipelineRunStatus;
import com.prgate.cicd.domain.model.enums.PipelineTrigger;
import com.prgate.cicd.domain.model.enums.QualityGateStatus;
import com.prgate.cicd.domain.model.valueobject.GitCommitSha;
import com.prgate.cicd.domain.model.valueobject.GitHubRepository;
import com.prgate.cicd.domain.model.valueobject.GitHubRunAttempt;
import com.prgate.cicd.domain.model.valueobject.GitHubWorkflowRunId;
import com.prgate.cicd.domain.model.valueobject.PipelineRunId;
import com.prgate.cicd.domain.model.valueobject.SonarAnalysisResult;
import com.prgate.cicd.domain.model.valueobject.SonarMetrics;
import com.prgate.cicd.infrastructure.persistence.jpa.entity.PipelineRunEntity;
import org.springframework.stereotype.Component;

import java.net.URI;

@Component
public class PipelineRunEntityMapper {

    public PipelineRunEntity toEntity(PipelineRun pipelineRun) {
        PipelineRunEntity entity = PipelineRunEntity.create();
        entity.setId(pipelineRun.getId().value());
        entity.setGithubRepositoryId(pipelineRun.getRepository().id());
        entity.setRepositoryOwner(pipelineRun.getRepository().owner());
        entity.setRepositoryName(pipelineRun.getRepository().name());
        entity.setGithubRunId(pipelineRun.getGithubRunId().value());
        entity.setGithubRunAttempt(pipelineRun.getGithubRunAttempt().value());
        entity.setWorkflowName(pipelineRun.getWorkflowName());
        entity.setBranchName(pipelineRun.getBranch());
        entity.setCommitSha(pipelineRun.getCommitSha().value());
        entity.setTriggerType(pipelineRun.getTrigger().name());
        entity.setRunUrl(pipelineRun.getRunUrl().toString());
        entity.setRunStatus(pipelineRun.getStatus().name());
        entity.setConclusion(pipelineRun.getConclusion().map(Enum::name).orElse(null));
        entity.setCreatedAt(pipelineRun.getCreatedAt());
        entity.setStartedAt(pipelineRun.getStartedAt().orElse(null));
        entity.setCompletedAt(pipelineRun.getCompletedAt().orElse(null));
        entity.setUpdatedAt(pipelineRun.getUpdatedAt());

        pipelineRun.getSonarAnalysis().ifPresent(analysis -> {
            entity.setSonarAnalysisId(analysis.analysisId());
            entity.setSonarQualityGateStatus(analysis.qualityGateStatus().name());
            entity.setSonarCoveragePercent(analysis.metrics().coveragePercent());
            entity.setSonarVulnerabilities(analysis.metrics().vulnerabilities());
            entity.setSonarDuplicatedLinesPercent(analysis.metrics().duplicatedLinesPercent());
            entity.setSonarTechnicalDebtPercent(analysis.metrics().technicalDebtPercent());
            entity.setSonarDashboardUrl(analysis.dashboardUrl().toString());
            entity.setSonarAnalyzedAt(analysis.analyzedAt());
        });
        return entity;
    }

    public PipelineRun toDomain(PipelineRunEntity entity) {
        SonarAnalysisResult sonarAnalysis = null;
        if (entity.getSonarAnalysisId() != null) {
            sonarAnalysis = new SonarAnalysisResult(
                    entity.getSonarAnalysisId(),
                    QualityGateStatus.valueOf(entity.getSonarQualityGateStatus()),
                    new SonarMetrics(entity.getSonarCoveragePercent(), entity.getSonarVulnerabilities(),
                            entity.getSonarDuplicatedLinesPercent(), entity.getSonarTechnicalDebtPercent()),
                    URI.create(entity.getSonarDashboardUrl()),
                    entity.getSonarAnalyzedAt());
        }

        return PipelineRun.reconstitute(
                new PipelineRunId(entity.getId()),
                new GitHubWorkflowRunId(entity.getGithubRunId()),
                new GitHubRunAttempt(entity.getGithubRunAttempt()),
                new GitHubRepository(entity.getGithubRepositoryId(), entity.getRepositoryOwner(),
                        entity.getRepositoryName()),
                entity.getWorkflowName(),
                entity.getBranchName(),
                new GitCommitSha(entity.getCommitSha()),
                PipelineTrigger.valueOf(entity.getTriggerType()),
                URI.create(entity.getRunUrl()),
                entity.getCreatedAt(),
                PipelineRunStatus.valueOf(entity.getRunStatus()),
                entity.getConclusion() == null ? null : PipelineConclusion.valueOf(entity.getConclusion()),
                entity.getStartedAt(),
                entity.getCompletedAt(),
                entity.getUpdatedAt(),
                sonarAnalysis);
    }
}
