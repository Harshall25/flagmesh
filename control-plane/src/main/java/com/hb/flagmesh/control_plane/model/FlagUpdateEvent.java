package com.hb.flagmesh.control_plane.model;

import java.util.List;
import java.util.Map;

/**
 * Message published to Redis channel "flags:updates" after a change commits.
 * SHARED CONTRACT with the Go sidecar: the JSON field names (flagKey, enabled, rolloutPct,
 * allowUsers, payload, version) must match the Go struct tags exactly.
 */
public record FlagUpdateEvent(
        String flagKey,
        boolean enabled,
        int rolloutPct,
        List<String> allowUsers,
        Map<String, Object> payload,
        long version
) {
}