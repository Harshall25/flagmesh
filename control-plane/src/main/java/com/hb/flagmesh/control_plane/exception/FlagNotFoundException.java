package com.hb.flagmesh.control_plane.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/**
 * No flag with the given key exists.
 * Spring MVC turns an ErrorResponseException into an HTTP 404 response with an
 * RFC 9457 ProblemDetail body, so no @RestControllerAdvice is needed.
 */
public class FlagNotFoundException extends ErrorResponseException {

    public FlagNotFoundException(String flagKey) {
        super(HttpStatus.NOT_FOUND,
                ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "Flag not found: " + flagKey),
                null);
    }
}
