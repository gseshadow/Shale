package com.shale.server.controller;

import java.time.Instant;
import java.sql.SQLException;
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

    @ExceptionHandler(com.shale.core.validation.FieldValidationException.class)
    ResponseEntity<ApiErrorResponse> handleFieldValidation(com.shale.core.validation.FieldValidationException ex,HttpServletRequest request){
        return ResponseEntity.badRequest().body(new ApiErrorResponse(Instant.now(),400,"validation_failed",ex.getMessage(),request.getRequestURI(),ex.errors()));
    }

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<ApiErrorResponse> handleResponseStatus(ResponseStatusException ex, HttpServletRequest request) {
        HttpStatus status = HttpStatus.valueOf(ex.getStatusCode().value());
        return error(status, messageOrDefault(ex.getReason(), status.getReasonPhrase()), request);
    }

    @ExceptionHandler({
            BindException.class,
            org.springframework.http.converter.HttpMessageNotReadableException.class,
            ConstraintViolationException.class,
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class
    })
    ResponseEntity<ApiErrorResponse> handleInvalidRequest(Exception ex, HttpServletRequest request) {
        log.error("Invalid request exceptionClass={} requestUri={}.",
                ex.getClass().getName(), request.getRequestURI(), sanitizedThrowable(ex));
        return error(HttpStatus.BAD_REQUEST, "Invalid request.", request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ApiErrorResponse> handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest request) {
        log.error("Invalid request exceptionClass={} requestUri={}.",
                ex.getClass().getName(), request.getRequestURI(), sanitizedThrowable(ex));
        return error(HttpStatus.BAD_REQUEST, "Invalid request.", request);
    }

    @ExceptionHandler(NoSuchElementException.class)
    ResponseEntity<ApiErrorResponse> handleNotFound(NoSuchElementException ex, HttpServletRequest request) {
        return error(HttpStatus.NOT_FOUND, "Resource not found.", request);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
        Throwable diagnostic;
        String causes;
        String sql;
        try {
            diagnostic = sanitizedThrowable(ex);
            causes = causeClasses(ex);
            sql = sqlMetadata(ex);
        } catch (Throwable diagnosticFailure) {
            diagnostic = fallbackDiagnostic(ex);
            causes = ex.getClass().getName();
            sql = "[]";
        }
        log.error("Unexpected server exception exceptionClass={} causeClasses={} sqlExceptions={}.",
                ex.getClass().getName(), causes, sql, diagnostic);
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
        Throwable cause = sanitizedThrowable(failure.getCause(), seen);
        var sanitized = new SanitizedDiagnosticException(failure.getClass().getName(), cause);
        sanitized.setStackTrace(failure.getStackTrace());
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

    private static String sqlMetadata(Throwable failure) {
        var values = new StringJoiner(", ", "[", "]");
        var causes = new IdentityHashMap<Throwable, Boolean>();
        var sqlExceptions = new IdentityHashMap<SQLException, Boolean>();
        for (Throwable current = failure; current != null && causes.put(current, Boolean.TRUE) == null;
                current = current.getCause()) {
            if (current instanceof SQLException sql) {
                for (SQLException chained = sql; chained != null && sqlExceptions.put(chained, Boolean.TRUE) == null;
                        chained = chained.getNextException()) {
                    values.add("{exceptionClass=" + chained.getClass().getName()
                            + ",sqlState=" + safeSqlState(chained.getSQLState())
                            + ",vendorCode=" + chained.getErrorCode() + "}");
                }
            }
        }
        return values.toString();
    }

    private static String safeSqlState(String value) {
        return value != null && value.matches("[A-Za-z0-9]{1,16}") ? value : "UNKNOWN";
    }

    private static Throwable fallbackDiagnostic(Throwable failure) {
        var fallback = new SanitizedDiagnosticException(failure.getClass().getName(), null);
        try { fallback.setStackTrace(failure.getStackTrace()); }
        catch (Throwable ignored) { fallback.setStackTrace(new StackTraceElement[0]); }
        return fallback;
    }

    private static final class SanitizedDiagnosticException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        private SanitizedDiagnosticException(String exceptionClass, Throwable cause) {
            super("Sanitized diagnostic for " + exceptionClass, cause, true, true);
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
