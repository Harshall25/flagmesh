package com.hb.flagmesh.control_plane.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/**
 * A flag with the given key already exists (primary key violation on create).
 * Rendered by Spring MVC as HTTP 409 with a ProblemDetail body.
 */
public class FlagAlreadyExistsException extends ErrorResponseException {

    public FlagAlreadyExistsException(String flagKey, Throwable cause) {
        super(HttpStatus.CONFLICT,
                ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "Flag already exists: " + flagKey),
                cause);
    }
}
