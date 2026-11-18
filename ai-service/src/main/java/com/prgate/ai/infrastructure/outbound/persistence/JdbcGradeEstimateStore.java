package com.prgate.ai.infrastructure.outbound.persistence;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.prgate.ai.application.dto.GradeEstimateResult;
import com.prgate.ai.application.port.out.GradeEstimateStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.dao.DuplicateKeyException;
import java.util.Optional;

@Repository
public class JdbcGradeEstimateStore implements GradeEstimateStore {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    public JdbcGradeEstimateStore(JdbcTemplate jdbc,ObjectMapper json) { this.jdbc=jdbc; this.json=json; }
    public Optional<GradeEstimateResult> find(String hash) {
        return jdbc.query("SELECT payload FROM grade_estimates WHERE request_hash=?",(row,i)->{
            try { return json.readValue(row.getString(1),GradeEstimateResult.class); }
            catch (java.io.IOException exception) { throw new IllegalStateException("Invalid stored estimate"); }
        },hash).stream().findFirst();
    }
    public GradeEstimateResult saveFirst(String hash,GradeEstimateResult result) {
        final String payload;
        try { payload=json.writeValueAsString(result); }
        catch (java.io.IOException exception) { throw new IllegalStateException("Cannot persist grade estimate"); }
        try { jdbc.update("INSERT INTO grade_estimates(request_hash,estimate_id,payload) VALUES (?,?,?)",hash,result.id(),payload); }
        catch (DuplicateKeyException exception) { return find(hash).orElseThrow(); }
        return result;
    }
}
