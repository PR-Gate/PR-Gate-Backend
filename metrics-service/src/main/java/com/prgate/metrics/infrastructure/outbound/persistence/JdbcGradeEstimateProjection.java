package com.prgate.metrics.infrastructure.outbound.persistence;
import com.prgate.metrics.application.port.out.GradeEstimateProjection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public class JdbcGradeEstimateProjection implements GradeEstimateProjection {
    private final JdbcTemplate jdbc;
    public JdbcGradeEstimateProjection(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public Optional<String> find(String hash) {
        return jdbc.query("SELECT payload FROM metrics.grade_estimate_projections WHERE evidence_hash=?",
                (row,i)->row.getString(1),hash).stream().findFirst();
    }
    public void save(String hash,String result) {
        jdbc.update("INSERT INTO metrics.grade_estimate_projections(evidence_hash,payload) VALUES (?,CAST(? AS jsonb)) ON CONFLICT DO NOTHING",hash,result);
    }
}
