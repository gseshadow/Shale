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
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(MinimizedCaseReadErrors.class);
    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiErrorResponse> failure(Exception failure, HttpServletRequest request) {
        int status = 503;
        String code = "case_read_unavailable", message = "Case read unavailable. Retry explicitly.";
        String category = "internal_failure";
        if (failure instanceof CaseReadException read) {
            category = read.kind().name();
            switch (read.kind()) {
                case DENIED -> { status = 403; code = "case_read_denied"; message = "Case read denied."; }
                case OVERSIZED -> { code = "case_payload_oversized"; message = "Case response exceeds the supported limit."; }
                case TIMEOUT -> { code = "case_read_timeout"; message = "Case read timed out. Retry explicitly."; }
                case AUDIT_UNAVAILABLE -> { code = "case_audit_unavailable"; message = "Required Case read audit unavailable."; }
                default -> { }
            }
        } else if (failure instanceof ResponseStatusException response) {
            status = response.getStatusCode().value(); code = "case_request_failed";
            category = status >= 500 ? "internal_failure" : "request_rejected";
            message = switch (status) {
                case 400 -> "Invalid Case request.";
                case 401 -> "Authentication required.";
                case 403 -> "Case read denied.";
                case 404 -> "Case unavailable.";
                default -> "Case read unavailable. Retry explicitly.";
            };
        } else if (failure instanceof org.springframework.http.converter.HttpMessageNotReadableException
                || failure instanceof org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
                || failure instanceof org.springframework.web.bind.MissingServletRequestParameterException) {
            category = "request_validation";
            status = 400; code = "invalid_case_request"; message = "Invalid Case request.";
        }
        // Argument-name resolution and adapter/mapper failures are internal, even when they throw
        // IllegalArgumentException. Only the typed request failures above establish a client error.
        // No URI, values, messages, causes or throwable argument may enter this diagnostic.
        log.warn("Minimized Case read operation={} category={} exceptionClass={}",
                operation(request), category, failure.getClass().getName());
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(
                new ApiErrorResponse(Instant.now(), status, code, message, request.getRequestURI()));
    }
    private static String operation(HttpServletRequest request) {
        Object pattern = request.getAttribute(org.springframework.web.servlet.HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        if (!(pattern instanceof String path)) return "unknown";
        return switch (path) {
            case "/api/v2/cases/search-page" -> "search";
            case "/api/v2/cases/assigned-page" -> "assigned";
            case "/api/v2/cases/{caseId}/overview" -> "overview";
            default -> "unknown";
        };
    }
}
