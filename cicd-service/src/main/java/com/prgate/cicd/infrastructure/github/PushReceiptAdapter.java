package com.prgate.cicd.infrastructure.github;

import com.prgate.cicd.application.port.out.PushReceiptPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import java.util.UUID;

@Component
public class PushReceiptAdapter implements PushReceiptPort {
    private final JdbcTemplate jdbc;
    public PushReceiptAdapter(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public boolean record(UUID deliveryId, long repositoryId, String owner, String repository,
                          String ref, String before, String after, boolean deleted, int commits) {
        return jdbc.update("""
                INSERT INTO cicd.github_push_receipts
                (delivery_id, repository_id, owner_login, repository_name, git_ref,
                 before_sha, after_sha, deleted, commits_in_payload)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT (delivery_id) DO NOTHING
                """, deliveryId, repositoryId, owner, repository, ref, before, after, deleted, commits) == 1;
    }
}
