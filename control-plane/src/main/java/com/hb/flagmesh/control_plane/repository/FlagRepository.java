package com.hb.flagmesh.control_plane.repository;

import com.hb.flagmesh.control_plane.model.FeatureFlag;
import com.hb.flagmesh.control_plane.model.FlagAuditLog;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
public class FlagRepository {

    private static final String FLAG_COLUMNS =
            "flag_key, enabled, rollout_pct, allow_users, payload, version, updated_at";
    private static final String AUDIT_COLUMNS =
            "id, flag_key, actor, old_state, new_state, changed_at";

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final RowMapper<FeatureFlag> flagMapper = this::mapFlag;
    private final RowMapper<FlagAuditLog> auditMapper = this::mapAudit;

    public FlagRepository (JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * 1. Implement the find all method to retrieve all feature flags from the database.
     */
    public List<FeatureFlag> findAll(){
        String sql = "SELECT " + FLAG_COLUMNS + " FROM flags.feature_flags";
        return jdbcTemplate.query(sql, flagMapper);
    }

    /**
     * 2. Implement the find by key method to retrieve a feature flag by its key.
     * flag_key is the primary key, so this returns at most one row.
     */
    public Optional<FeatureFlag> findFlagByKey(String flagKey){
        String sql = "SELECT " + FLAG_COLUMNS + " FROM flags.feature_flags WHERE flag_key = ?";
        return jdbcTemplate.query(sql, flagMapper, flagKey).stream().findFirst();
    }

    /**
     * 2b. Same lookup, but locks the row for the rest of the transaction
     * (SELECT ... FOR UPDATE). The mutation service calls this first so the audit
     * diff reads a state no concurrent edit can change. MUST run inside a
     * transaction, or Postgres releases the lock at once.
     */
    public Optional<FeatureFlag> findFlagByKeyForUpdate(String flagKey) {
        String sql = "SELECT " + FLAG_COLUMNS
                + " FROM flags.feature_flags WHERE flag_key = ? FOR UPDATE";
        return jdbcTemplate.query(sql, flagMapper, flagKey).stream().findFirst();
    }

    /**
     * 3. Implement the update method to update a feature flag in the database.
     * Returns the number of rows changed: the service checks it is 1. Zero means
     * the flag vanished or a concurrent edit lost the row.
     */
    public int updateFlag(FeatureFlag flag) {
        String sql = "UPDATE flags.feature_flags SET enabled = ?, rollout_pct = ?, allow_users = CAST(? AS jsonb), payload = CAST(? AS jsonb), version = ?, updated_at = ? WHERE flag_key = ?";
        return jdbcTemplate.update(sql,
                flag.enabled(), flag.rolloutPct(),
                objectMapper.writeValueAsString(flag.allowUsers()),
                objectMapper.writeValueAsString(flag.payload()),
                flag.version(),
                flag.updatedAt(),
                flag.flagKey()
        );
    }

    /**
     * 4. Implement the insert audit log method to insert a new audit log entry into the database.
     */
    public void insertAuditLog(FlagAuditLog flagAuditLog) {
        String sql = "INSERT INTO flags.flag_audit_logs (" + AUDIT_COLUMNS + ") VALUES (?, ?, ?, CAST(? AS jsonb), CAST(? AS jsonb), ?)";
        jdbcTemplate.update(
                sql,
                flagAuditLog.id(),
                flagAuditLog.flagKey(),
                flagAuditLog.actor(),
                flagAuditLog.oldState() == null ? null : objectMapper.writeValueAsString(flagAuditLog.oldState()),
                objectMapper.writeValueAsString(flagAuditLog.newState()),
                flagAuditLog.changedAt()
        );
    }

    /**
     * 5. Implement the find audit log by flag key method to find all audit logs for a specific feature flag.
     */
    public List<FlagAuditLog> findAuditLogByFlagKey(String flagKey){
        String sql = "SELECT " + AUDIT_COLUMNS
                + " FROM flags.flag_audit_logs WHERE flag_key = ? ORDER BY changed_at DESC";
        return jdbcTemplate.query(sql, auditMapper, flagKey);
    }

    /**
     * 6. Implement the find audit log by id method to find a specific audit log entry by its ID.
     */
    public Optional<FlagAuditLog> findAuditLogById(UUID id) {
        String sql = "SELECT " + AUDIT_COLUMNS + " FROM flags.flag_audit_logs WHERE id = ?";
        return jdbcTemplate.query(sql, auditMapper, id).stream().findFirst();
    }

    public void insertFlag(FeatureFlag f) {
        String sql = "INSERT INTO flags.feature_flags (flag_key, enabled, rollout_pct, allow_users, payload, version, updated_at)"
                + " VALUES (?, ?, ?, CAST(? AS jsonb), CAST(? AS jsonb), ?, ?)";
        jdbcTemplate.update(
                sql,
                f.flagKey(),
                f.enabled(),
                f.rolloutPct(),
                objectMapper.writeValueAsString(f.allowUsers()),
                objectMapper.writeValueAsString(f.payload()),
                f.version(),
                f.updatedAt()
        );
    }

    private FeatureFlag mapFlag(ResultSet rs, int rowNum) throws SQLException {
        return new FeatureFlag(
                rs.getString("flag_key"),
                rs.getBoolean("enabled"),
                rs.getInt("rollout_pct"),
                parseUsers(rs.getString("allow_users")),
                parsePayload(rs.getString("payload")),
                rs.getLong("version"),
                rs.getObject("updated_at", OffsetDateTime.class)
        );
    }

    private FlagAuditLog mapAudit(ResultSet rs, int rowNum) throws SQLException {
        return new FlagAuditLog(
                rs.getObject("id", UUID.class),
                rs.getString("flag_key"),
                rs.getString("actor"),
                parseStateOrNull(rs.getString("old_state")),
                parsePayload(rs.getString("new_state")),
                rs.getObject("changed_at", OffsetDateTime.class)
        );
    }

    private List<String> parseUsers(String json) {
        if (json == null || json.isBlank()) return List.of();
        return objectMapper.readValue(json, new TypeReference<List<String>>(){});
    }

    // payload and new_state are NOT NULL JSONB objects: empty map for a blank read.
    private Map<String, Object> parsePayload(String json) {
        if (json == null || json.isBlank()) return Map.of();
        return objectMapper.readValue(json, new TypeReference<Map<String,Object>>(){});
    }

    // old_state is NULL for a newly created flag; keep that null instead of crashing.
    private Map<String, Object> parseStateOrNull(String json) {
        if (json == null || json.isBlank()) return null;
        return objectMapper.readValue(json, new TypeReference<Map<String,Object>>(){});
    }

}
