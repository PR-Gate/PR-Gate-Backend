package com.prgate.cicd.application.port.out;

import java.util.UUID;

public interface PushReceiptPort {
    boolean record(UUID deliveryId, long repositoryId, String owner, String repository,
                   String ref, String before, String after, boolean deleted, int commits);
}
