package com.hb.flagmesh.control_plane.model;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * One row of flags.feature_flags.
 * A record is immutable: a change produces a new FeatureFlag, never an edit in place.
 */
public record FeatureFlag(
        String flagKey,
        boolean enabled,
        int rolloutPct,
        List<String> allowUsers,
        Map<String, Object> payload,
        long version,
        OffsetDateTime updatedAt
) {
}