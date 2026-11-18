package com.prgate.metrics.application.port.in;

import com.prgate.metrics.application.dto.AnalysisCompletedEvent;

public interface ReceiveAnalysisUseCase {
    boolean receive(AnalysisCompletedEvent event);
}
