package com.prgate.cicd.infrastructure.sonar;

import com.prgate.cicd.application.port.out.SonarWebhookInboxPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Repository
public class SonarWebhookInboxAdapter implements SonarWebhookInboxPort {
    private final JdbcTemplate jdbc;
    public SonarWebhookInboxAdapter(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    @Override
    public void enqueue(byte[] payload) {
        try {
            String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(payload));
            jdbc.update("""
                    INSERT INTO cicd.sonar_webhook_inbox(payload_hash, payload) VALUES (?, ?)
                    ON CONFLICT(payload_hash) DO NOTHING
                    """, hash, new String(payload, StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
