package com.prgate.cicd.application.port.out;

import com.prgate.cicd.application.model.GitHubDeliveryId;

import java.time.Instant;

public interface WebhookDeliveryRepository {

    /**
     * Claims a delivery id if it has not been processed before.
     * Implementations must participate in the use case transaction so a failed
     * pipeline update also rolls back this claim.
     *
     * @return true when this call claimed the id; false when it was already claimed
     */
    boolean claimIfUnprocessed(GitHubDeliveryId deliveryId, Instant receivedAt);
}
