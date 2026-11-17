package com.prgate.cicd.infrastructure.sonar;

import com.prgate.cicd.application.port.out.SonarWebhookReceiptPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Component
public class SonarWebhookReceiptAdapter implements SonarWebhookReceiptPort {
    private final JdbcTemplate jdbc;
    public SonarWebhookReceiptAdapter(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public boolean record(byte[] payload, String projectKey, String taskId, String status) {
        try {
            String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(payload));
            return jdbc.update("""
                    INSERT INTO cicd.sonar_webhook_receipts (payload_hash, project_key, task_id, status, payload)
                    VALUES (?, ?, ?, ?, ?) ON CONFLICT (payload_hash) DO NOTHING
                    """, hash, projectKey, taskId, status, new String(payload, StandardCharsets.UTF_8)) == 1;
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }
}
