package com.prgate.cicd.application.port.out;

public interface SonarWebhookInboxPort {
    /** Store an authenticated payload durably; an exact duplicate is harmless. */
    void enqueue(byte[] payload);
}
