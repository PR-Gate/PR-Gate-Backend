package com.prgate.cicd.application.port.in;

import com.prgate.cicd.application.model.RecordSonarAnalysisCommand;

public interface RecordSonarAnalysisUseCase {
    void record(RecordSonarAnalysisCommand command);
}
