package com.hb.flagmesh.control_plane.model;

import java.util.List;
import java.util.Map;

/**
 * Body of PATCH /api/flags/{key}.
 * Wrapper types (Boolean, Integer) on purpose: null means "field not sent, keep the current value".
 * A primitive boolean would turn "not sent" into false and switch the flag off by accident.
 */
public record UpdateFlagRequest(
        Boolean enabled,
        Integer rolloutPct,
        List<String> allowUsers,
        Map<String, Object> payload
) {
}