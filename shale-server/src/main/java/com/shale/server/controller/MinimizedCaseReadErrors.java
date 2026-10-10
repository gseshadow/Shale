package com.shale.server.controller;

import java.time.Instant;
import com.shale.core.service.CaseReadException;
import com.shale.server.dto.ApiErrorResponse;
import org.springframework.core.annotation.Order;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import jakarta.servlet.http.HttpServletRequest;

/** Scoped errors never echo request bodies, source values or exception messages, including malformed JSON. */
@Order(-1)
@RestControllerAdvice(assignableTypes = MinimizedCaseReadController.class)
public final class MinimizedCaseReadErrors {
    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiErrorResponse> failure(Exception failure, HttpServletRequest request) {
        int status = 503;
        String code = "case_read_unavailable", message = "Case read unavailable. Retry explicitly.";
        if (failure instanceof CaseReadException read) {
            switch (read.kind()) {
                case DENIED -> { status = 403; code = "case_read_denied"; message = "Case read denied."; }
                case OVERSIZED -> { code = "case_payload_oversized"; message = "Case response exceeds the supported limit."; }
                case TIMEOUT -> { code = "case_read_timeout"; message = "Case read timed out. Retry explicitly."; }
                case AUDIT_UNAVAILABLE -> { code = "case_audit_unavailable"; message = "Required Case read audit unavailable."; }
                default -> { }
            }
        } else if (failure instanceof ResponseStatusException response) {
            status = response.getStatusCode().value(); code = "case_request_failed";
            message = switch (status) { case 401 -> "Authentication required."; case 404 -> "Case unavailable."; default -> "Invalid Case request."; };
        } else if (failure instanceof IllegalArgumentException
                || failure instanceof org.springframework.http.converter.HttpMessageNotReadableException
                || failure instanceof org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
                || failure instanceof org.springframework.web.bind.MissingServletRequestParameterException) {
            status = 400; code = "invalid_case_request"; message = "Invalid Case request.";
        }
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(
                new ApiErrorResponse(Instant.now(), status, code, message, request.getRequestURI()));
    }
}
