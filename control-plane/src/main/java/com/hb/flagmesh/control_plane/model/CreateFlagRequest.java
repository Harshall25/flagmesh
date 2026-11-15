package com.hb.flagmesh.control_plane.model;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;

/**
 * Body of POST /api/flags.
 * Only the fields a client may set: version and updated_at are always server-controlled.
 * flagKey follows the seed naming style: lowercase, digits, dots, dashes, underscores
 * (e.g. "payment.rate-limit"), at most 100 chars to fit the VARCHAR(100) primary key.
 */
public record CreateFlagRequest(
        @NotBlank @Size(max = 100) @Pattern(regexp = "[a-z0-9][a-z0-9._-]*") String flagKey,
        Boolean enabled,
        @Min(0) @Max(100) Integer rolloutPct,
        List<String> allowUsers,
        Map<String, Object> payload
) {
    public UpdateFlagRequest toUpdateRequest() {
        return new UpdateFlagRequest(enabled, rolloutPct, allowUsers, payload);
    }
}
