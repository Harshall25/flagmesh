package com.hb.flagmesh.control_plane.model;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.util.List;
import java.util.Map;

/**
 * Body of PATCH /api/flags/{key}.
 * Wrapper types (Boolean, Integer) on purpose: null means "field not sent, keep the current value".
 * A primitive boolean would turn "not sent" into false and switch the flag off by accident.
 * The controller must take this with @Valid: a bad rolloutPct then becomes HTTP 400
 * before any SQL runs (the DB CHECK constraint is the second line of defence).
 */
public record UpdateFlagRequest(
        Boolean enabled,
        @Min(0) @Max(100) Integer rolloutPct,
        List<String> allowUsers,
        Map<String, Object> payload
) {
}