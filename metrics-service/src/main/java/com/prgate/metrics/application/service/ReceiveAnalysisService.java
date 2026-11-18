package com.prgate.metrics.application.service;

import com.prgate.metrics.application.dto.AnalysisCompletedEvent;
import com.prgate.metrics.application.port.in.ReceiveAnalysisUseCase;
import com.prgate.metrics.application.port.out.AnalysisSnapshotStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Persist the technical evidence first; a Quality Gate alone is not a rubric score. */
@Service
public class ReceiveAnalysisService implements ReceiveAnalysisUseCase {
    private final AnalysisSnapshotStore store;

    public ReceiveAnalysisService(AnalysisSnapshotStore store) { this.store = store; }

    @Transactional
    @Override
    public boolean receive(AnalysisCompletedEvent event) {
        return store.saveIfAbsent(java.util.Objects.requireNonNull(event));
    }
}
