package com.prgate.ai.infrastructure.outbound.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.*;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;

/** Short atomic writes; no database transaction spans a remote LLM call. */
abstract class JdbcAggregateRepository<T> {
    protected final ObjectMapper mapper;
    private final JdbcTemplate jdbc;
    private final String kind;
    // Loaded aggregate identity -> original row version. Weak keys do not retain detached aggregates.
    private final Map<T, Long> loadedVersions = Collections.synchronizedMap(new WeakHashMap<>());

    protected JdbcAggregateRepository(JdbcTemplate jdbc, ObjectMapper mapper, String kind) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.kind = kind;
    }

    protected Optional<T> load(String id) {
        var rows = jdbc.query("SELECT version, payload FROM ai_documents WHERE kind=? AND id=?",
                (rs, row) -> new Stored(rs.getLong("version"), rs.getString("payload")), kind, id);
        if (rows.isEmpty()) return Optional.empty();
        var row = rows.getFirst();
        T aggregate = decode(row.payload());
        loadedVersions.put(aggregate, row.version());
        return Optional.of(aggregate);
    }

    protected synchronized void store(String id, T aggregate) {
        String payload = encode(aggregate);
        Long originalVersion = loadedVersions.get(aggregate);
        if (originalVersion == null) {
            try {
                jdbc.update("INSERT INTO ai_documents(kind,id,version,payload) VALUES(?,?,?,?)", kind, id, 0L, payload);
            } catch (DuplicateKeyException ex) {
                throw new OptimisticLockingFailureException("Aggregate already exists; reload before saving");
            }
            loadedVersions.put(aggregate, 0L);
        } else {
            int changed = jdbc.update("UPDATE ai_documents SET version=?,payload=? WHERE kind=? AND id=? AND version=?",
                    originalVersion + 1, payload, kind, id, originalVersion);
            if (changed != 1) throw new OptimisticLockingFailureException("Aggregate changed; reload before retrying");
            loadedVersions.put(aggregate, originalVersion + 1);
        }
    }

    protected String json(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (Exception ex) { throw new DataRetrievalFailureException("Cannot encode aggregate snapshot", ex); }
    }
    protected <R> R parse(String value, Class<R> type) {
        try { return mapper.readValue(value, type); }
        catch (Exception ex) { throw new DataRetrievalFailureException("Cannot decode aggregate snapshot", ex); }
    }
    protected abstract T decode(String payload);
    protected abstract String encode(T aggregate);
    private record Stored(long version, String payload) {}
}
