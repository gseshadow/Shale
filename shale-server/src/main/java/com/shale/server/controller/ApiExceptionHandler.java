package com.shale.server.controller;

import java.time.Instant;
import java.util.IdentityHashMap;
import java.util.NoSuchElementException;
import java.util.StringJoiner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

import com.shale.server.dto.ApiErrorResponse;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;

@RestControllerAdvice
public final class ApiExceptionHandler {
	private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<ApiErrorResponse> handleResponseStatus(ResponseStatusException ex, HttpServletRequest request) {
        HttpStatus status = HttpStatus.valueOf(ex.getStatusCode().value());
        return error(status, messageOrDefault(ex.getReason(), status.getReasonPhrase()), request);
    }

    @ExceptionHandler({
            BindException.class,
            ConstraintViolationException.class,
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class
    })
    ResponseEntity<ApiErrorResponse> handleInvalidRequest(Exception ex, HttpServletRequest request) {
        log.error("Invalid request exceptionClass={} requestUri={}.",
                ex.getClass().getName(), request.getRequestURI(), ex);
        return error(HttpStatus.BAD_REQUEST, "Invalid request.", request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ApiErrorResponse> handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest request) {
        log.error("Invalid request exceptionClass={} requestUri={}.",
                ex.getClass().getName(), request.getRequestURI(), ex);
        return error(HttpStatus.BAD_REQUEST, "Invalid request.", request);
    }

    @ExceptionHandler(NoSuchElementException.class)
    ResponseEntity<ApiErrorResponse> handleNotFound(NoSuchElementException ex, HttpServletRequest request) {
        return error(HttpStatus.NOT_FOUND, "Resource not found.", request);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unexpected server exception exceptionClass={} causeClasses={}.",
                ex.getClass().getName(), causeClasses(ex), sanitizedThrowable(ex));
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error.", request);
    }

    /**
     * Produces a throwable suitable for server diagnostics without copying exception messages. Exception messages
     * are not a safe logging boundary because framework, JDBC, or future adapter failures can echo credentials or
     * request bodies. The exception/cause types and original stack frames retain the failure location and chain.
     */
    private static Throwable sanitizedThrowable(Throwable failure) {
        return sanitizedThrowable(failure, new IdentityHashMap<>());
    }

    private static Throwable sanitizedThrowable(Throwable failure, IdentityHashMap<Throwable, Boolean> seen) {
        if (failure == null || seen.put(failure, Boolean.TRUE) != null) return null;
        var sanitized = new SanitizedDiagnosticException(failure.getClass().getName());
        sanitized.setStackTrace(failure.getStackTrace());
        Throwable cause = sanitizedThrowable(failure.getCause(), seen);
        if (cause != null) sanitized.initCause(cause);
        for (Throwable suppressed : failure.getSuppressed()) {
            Throwable safeSuppressed = sanitizedThrowable(suppressed, seen);
            if (safeSuppressed != null) sanitized.addSuppressed(safeSuppressed);
        }
        return sanitized;
    }

    private static String causeClasses(Throwable failure) {
        var classes = new StringJoiner(" -> ");
        var seen = new IdentityHashMap<Throwable, Boolean>();
        for (Throwable current = failure; current != null && seen.put(current, Boolean.TRUE) == null;
                current = current.getCause()) {
            classes.add(current.getClass().getName());
        }
        return classes.toString();
    }

    private static final class SanitizedDiagnosticException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        private SanitizedDiagnosticException(String exceptionClass) {
            super("Sanitized diagnostic for " + exceptionClass, null, true, true);
        }
    }

    private static ResponseEntity<ApiErrorResponse> error(HttpStatus status, String message, HttpServletRequest request) {
        return ResponseEntity.status(status).body(new ApiErrorResponse(
                Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                message,
                request.getRequestURI()));
    }

    private static String messageOrDefault(String message, String defaultMessage) {
        return message == null || message.isBlank() ? defaultMessage : message;
    }
}
