package com.hb.flagmesh.control_plane.service;

import com.hb.flagmesh.control_plane.exception.FlagAlreadyExistsException;
import com.hb.flagmesh.control_plane.exception.FlagNotFoundException;
import com.hb.flagmesh.control_plane.model.FeatureFlag;
import com.hb.flagmesh.control_plane.model.FlagAuditLog;
import com.hb.flagmesh.control_plane.model.FlagUpdateEvent;
import com.hb.flagmesh.control_plane.model.UpdateFlagRequest;
import com.hb.flagmesh.control_plane.repository.FlagRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Every flag write goes through here. Each public method is one transaction:
 * the flag row, its audit row and the change event commit together or not at all.
 * The Redis publish happens only after the commit (see FlagUpdatePublisher).
 */
@Service
@Transactional
public class FlagMutationService {

    private final FlagRepository flagRepository;
    private final ApplicationEventPublisher events;
    private final ObjectMapper objectMapper;

    public FlagMutationService(FlagRepository flagRepository, ApplicationEventPublisher events, ObjectMapper objectMapper) {
        this.flagRepository = flagRepository;
        this.events = events;
        this.objectMapper = objectMapper;
    }

    /**
     * Creates a flag at version 1. Fields left out of the request get the same
     * defaults as the database columns. A duplicate key becomes HTTP 409.
     * @param flagKey the primary key of the new flag
     * @param req the request body, with optional fields
     * @param actor the user or service that made the change, for the audit log
     * @return the new flag, with all fields filled in
     * @throws FlagAlreadyExistsException if a flag with the same key already exists
     */
    public FeatureFlag createFlag(String flagKey, UpdateFlagRequest req, String actor) {
        FeatureFlag created = new FeatureFlag(
                flagKey,
                Boolean.TRUE.equals(req.enabled()),
                req.rolloutPct() != null ? req.rolloutPct() : 0,
                req.allowUsers() != null ? req.allowUsers() : List.of(),
                req.payload() != null ? req.payload() : Map.of(),
                1L,
                OffsetDateTime.now());
        try {
            flagRepository.insertFlag(created);
        } catch (DuplicateKeyException e) {
            // TODO(datadog): log.warn("create rejected, flag exists", flagKey, actor). Client error (409), not an alert.
            throw new FlagAlreadyExistsException(flagKey, e);
        }
        recordChange(actor, null, created);
        return created;
    }

    /**
     * Partial update (PATCH): a null field in the request keeps the current value.
     * The row is locked first (SELECT ... FOR UPDATE), so the audit's before-state
     * is exact and concurrent edits to the same flag run one after another.
     * @throws FlagNotFoundException if the flag does not exist
     */
    public FeatureFlag updateFlag(String flagKey, UpdateFlagRequest req, String actor) {
        FeatureFlag current = flagRepository.findFlagByKeyForUpdate(flagKey)
                .orElseThrow(() -> new FlagNotFoundException(flagKey));

        FeatureFlag updated = new FeatureFlag(
                current.flagKey(),
                req.enabled()    != null ? req.enabled()    : current.enabled(),
                req.rolloutPct() != null ? req.rolloutPct() : current.rolloutPct(),
                req.allowUsers() != null ? req.allowUsers() : current.allowUsers(),
                req.payload()    != null ? req.payload()    : current.payload(),
                current.version() + 1,
                OffsetDateTime.now()
        );

        if (flagRepository.updateFlag(updated) != 1) {
            //Cannot happen while the row lock is held; fail loudly and roll back if it does.
            // TODO(datadog): log.error("invariant broken: locked row not updated", flagKey, version). Alert on this.
            throw new IllegalStateException("Flag " + flagKey + " was not updated");
        }
        recordChange(actor, current, updated);
        return updated;
    }

    /**
     * Writes the audit row and queues the Redis event, both inside the current
     * transaction. before is null for a newly created flag.
     */
    private void recordChange(String actor, FeatureFlag before, FeatureFlag after) {
        // No logging here: a failed insert throws and rolls back the whole change,
        // and success is logged after commit in FlagUpdatePublisher.
        FlagAuditLog auditLog = new FlagAuditLog(
                UUID.randomUUID(),
                after.flagKey(),
                actor,
                before == null ? null : toMap(before),
                toMap(after),
                after.updatedAt()
        );
        flagRepository.insertAuditLog(auditLog);

        events.publishEvent(new FlagUpdateEvent(
                after.flagKey(),
                after.enabled(),
                after.rolloutPct(),
                after.allowUsers(),
                after.payload(),
                after.version())
        );
    }

    private Map<String, Object> toMap(FeatureFlag flag) {
        return objectMapper.convertValue(flag, new TypeReference<Map<String, Object>>() {});
    }

    /**
     * Returns all flags, for GET /api/flags and the sidecar snapshot.
     */
    @Transactional(readOnly = true)
    public List<FeatureFlag> getAllFlags() {
        return flagRepository.findAll();
    }

    /**
     * Returns one flag, for GET /api/flags/{key}.
     * @throws FlagNotFoundException if the flag does not exist (HTTP 404)
     */
    @Transactional(readOnly = true)
    public FeatureFlag getFlag(String flagKey) {
        return flagRepository.findFlagByKey(flagKey)
                .orElseThrow(() -> new FlagNotFoundException(flagKey));
    }

    /**
     * Returns the audit log for a specific flag, newest first, for GET /api/flags/{key}/audits.
     */
    @Transactional(readOnly = true)
    public List<FlagAuditLog> getFlagAudits(String flagKey) {
        return flagRepository.findAuditLogByFlagKey(flagKey);
    }
}
