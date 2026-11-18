package com.prgate.metrics.application.port.out;

import com.prgate.metrics.application.dto.AnalysisCompletedEvent;

public interface AnalysisSnapshotStore {
    /** Returns false for an identical redelivery; rejects reuse of an ID with different data. */
    boolean saveIfAbsent(AnalysisCompletedEvent event);
}
