package com.prgate.cicd.infrastructure.persistence.jpa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "pipeline_runs",
        uniqueConstraints = @UniqueConstraint(name = "uq_pipeline_run_github_identity",
                columnNames = {"github_repository_id", "github_run_id", "github_run_attempt"}),
        indexes = {
                @Index(name = "ix_pipeline_runs_repository_branch_created",
                        columnList = "github_repository_id, branch_name, created_at"),
                @Index(name = "ix_pipeline_runs_created_at", columnList = "created_at")
        })
public class PipelineRunEntity {

    @Id
    private UUID id;

    @Column(name = "evaluation_repository_id")
    private UUID evaluationRepositoryId;

    @Column(name = "academic_project_id")
    private UUID academicProjectId;

    public UUID getEvaluationRepositoryId() { return evaluationRepositoryId; }
    public void setEvaluationRepositoryId(UUID value) { evaluationRepositoryId = value; }
    public UUID getAcademicProjectId() { return academicProjectId; }
    public void setAcademicProjectId(UUID value) { academicProjectId = value; }

    @Column(name = "github_repository_id", nullable = false)
    private long githubRepositoryId;

    @Column(name = "repository_owner", nullable = false, length = 100)
    private String repositoryOwner;

    @Column(name = "repository_name", nullable = false, length = 100)
    private String repositoryName;

    @Column(name = "github_run_id", nullable = false)
    private long githubRunId;

    @Column(name = "github_run_attempt", nullable = false)
    private int githubRunAttempt;

    @Column(name = "workflow_name", nullable = false, length = 200)
    private String workflowName;

    @Column(name = "branch_name", nullable = false, length = 255)
    private String branchName;

    @Column(name = "commit_sha", nullable = false, length = 64)
    private String commitSha;

    @Column(name = "trigger_type", nullable = false, length = 30)
    private String triggerType;

    @Column(name = "run_url", nullable = false, columnDefinition = "text")
    private String runUrl;

    @Column(name = "run_status", nullable = false, length = 30)
    private String runStatus;

    @Column(name = "conclusion", length = 30)
    private String conclusion;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "sonar_analysis_id")
    private String sonarAnalysisId;

    @Column(name = "sonar_quality_gate_status", length = 20)
    private String sonarQualityGateStatus;

    @Column(name = "sonar_coverage_percent", precision = 5, scale = 2)
    private BigDecimal sonarCoveragePercent;

    @Column(name = "sonar_vulnerabilities")
    private Integer sonarVulnerabilities;

    @Column(name = "sonar_duplicated_lines_percent", precision = 5, scale = 2)
    private BigDecimal sonarDuplicatedLinesPercent;

    @Column(name = "sonar_technical_debt_percent", precision = 5, scale = 2)
    private BigDecimal sonarTechnicalDebtPercent;

    @Column(name = "sonar_dashboard_url", columnDefinition = "text")
    private String sonarDashboardUrl;

    @Column(name = "sonar_analyzed_at")
    private Instant sonarAnalyzedAt;

    protected PipelineRunEntity() {
    }

    public static PipelineRunEntity create() {
        return new PipelineRunEntity();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public long getGithubRepositoryId() { return githubRepositoryId; }
    public void setGithubRepositoryId(long githubRepositoryId) { this.githubRepositoryId = githubRepositoryId; }
    public String getRepositoryOwner() { return repositoryOwner; }
    public void setRepositoryOwner(String repositoryOwner) { this.repositoryOwner = repositoryOwner; }
    public String getRepositoryName() { return repositoryName; }
    public void setRepositoryName(String repositoryName) { this.repositoryName = repositoryName; }
    public long getGithubRunId() { return githubRunId; }
    public void setGithubRunId(long githubRunId) { this.githubRunId = githubRunId; }
    public int getGithubRunAttempt() { return githubRunAttempt; }
    public void setGithubRunAttempt(int githubRunAttempt) { this.githubRunAttempt = githubRunAttempt; }
    public String getWorkflowName() { return workflowName; }
    public void setWorkflowName(String workflowName) { this.workflowName = workflowName; }
    public String getBranchName() { return branchName; }
    public void setBranchName(String branchName) { this.branchName = branchName; }
    public String getCommitSha() { return commitSha; }
    public void setCommitSha(String commitSha) { this.commitSha = commitSha; }
    public String getTriggerType() { return triggerType; }
    public void setTriggerType(String triggerType) { this.triggerType = triggerType; }
    public String getRunUrl() { return runUrl; }
    public void setRunUrl(String runUrl) { this.runUrl = runUrl; }
    public String getRunStatus() { return runStatus; }
    public void setRunStatus(String runStatus) { this.runStatus = runStatus; }
    public String getConclusion() { return conclusion; }
    public void setConclusion(String conclusion) { this.conclusion = conclusion; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public String getSonarAnalysisId() { return sonarAnalysisId; }
    public void setSonarAnalysisId(String sonarAnalysisId) { this.sonarAnalysisId = sonarAnalysisId; }
    public String getSonarQualityGateStatus() { return sonarQualityGateStatus; }
    public void setSonarQualityGateStatus(String sonarQualityGateStatus) { this.sonarQualityGateStatus = sonarQualityGateStatus; }
    public BigDecimal getSonarCoveragePercent() { return sonarCoveragePercent; }
    public void setSonarCoveragePercent(BigDecimal sonarCoveragePercent) { this.sonarCoveragePercent = sonarCoveragePercent; }
    public Integer getSonarVulnerabilities() { return sonarVulnerabilities; }
    public void setSonarVulnerabilities(Integer sonarVulnerabilities) { this.sonarVulnerabilities = sonarVulnerabilities; }
    public BigDecimal getSonarDuplicatedLinesPercent() { return sonarDuplicatedLinesPercent; }
    public void setSonarDuplicatedLinesPercent(BigDecimal sonarDuplicatedLinesPercent) { this.sonarDuplicatedLinesPercent = sonarDuplicatedLinesPercent; }
    public BigDecimal getSonarTechnicalDebtPercent() { return sonarTechnicalDebtPercent; }
    public void setSonarTechnicalDebtPercent(BigDecimal sonarTechnicalDebtPercent) { this.sonarTechnicalDebtPercent = sonarTechnicalDebtPercent; }
    public String getSonarDashboardUrl() { return sonarDashboardUrl; }
    public void setSonarDashboardUrl(String sonarDashboardUrl) { this.sonarDashboardUrl = sonarDashboardUrl; }
    public Instant getSonarAnalyzedAt() { return sonarAnalyzedAt; }
    public void setSonarAnalyzedAt(Instant sonarAnalyzedAt) { this.sonarAnalyzedAt = sonarAnalyzedAt; }
}
