package com.hb.flagmesh.control_plane.model;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * One row of flags.flag_audit_logs: who changed which flag, and the exact before/after state.
 * oldState is null when the flag was just created.
 */
public record FlagAuditLog(
        UUID id,
        String flagKey,
        String actor,
        Map<String, Object> oldState,
        Map<String, Object> newState,
        OffsetDateTime changedAt
) {
}