package com.prgate.cicd.application.port.out;

/** Stores delivery evidence independently of pipeline correlation. */
public interface SonarWebhookReceiptPort {
    boolean record(byte[] payload, String projectKey, String taskId, String status);
}
