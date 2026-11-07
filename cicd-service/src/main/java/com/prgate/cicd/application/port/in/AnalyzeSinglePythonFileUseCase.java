package com.prgate.cicd.application.port.in;

public interface AnalyzeSinglePythonFileUseCase {
    Result analyze(String filename, byte[] content);

    record Result(String projectKey, String analysisId, String qualityGateStatus, String dashboardUrl) { }
}
