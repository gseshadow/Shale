package com.shale.server.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.validation.ConstraintViolationException;

class ApiExceptionHandlerTest {
    private final ApiExceptionHandler handler = new ApiExceptionHandler();
    private final Logger logger = (Logger) LoggerFactory.getLogger(ApiExceptionHandler.class);
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void captureHandlerLogs() {
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void stopCapturingHandlerLogs() {
        logger.detachAppender(appender);
        appender.stop();
    }

    @Test
    void invalidRequestLogsDiagnosticAndKeepsClientResponseSanitized() {
        var request = new MockHttpServletRequest("POST", "/api/control-plane/application-releases/1.0.134/import");
        var exception = new ConstraintViolationException("sensitive binding diagnostic", Set.of());

        var response = handler.handleInvalidRequest(exception, request);

        assertNotNull(response.getBody(), "The error response must include its sanitized body");
        assertSanitizedBadRequest(response.getStatusCode(), response.getBody().status(), response.getBody().message(),
                response.getBody().path(), request.getRequestURI(), "sensitive binding diagnostic");
        assertDiagnosticLog(exception, request.getRequestURI());
    }

    @Test
    void illegalArgumentLogsDiagnosticAndKeepsClientResponseSanitized() {
        var request = new MockHttpServletRequest("POST", "/api/example");
        var exception = new IllegalArgumentException("sensitive argument diagnostic");

        var response = handler.handleIllegalArgument(exception, request);

        assertNotNull(response.getBody(), "The error response must include its sanitized body");
        assertSanitizedBadRequest(response.getStatusCode(), response.getBody().status(), response.getBody().message(),
                response.getBody().path(), request.getRequestURI(), "sensitive argument diagnostic");
        assertDiagnosticLog(exception, request.getRequestURI());
    }

    @Test
    void unexpectedFailureLogsSanitizedStackAndCauseChainWhileKeepingClientResponseGeneric() {
        var request = new MockHttpServletRequest("POST", "/api/auth/desktop-session");
        var cause = new java.sql.SQLException("password=hunter2 token=secret rememberedCredential=opaque body={secret}");
        var exception = new IllegalStateException("request body and response body are sensitive", cause);
        var originalStack = new StackTraceElement("com.shale.server.runtime.SqlRememberCredentialStore", "create",
                "SqlRememberCredentialStore.java", 14);
        exception.setStackTrace(new StackTraceElement[] { originalStack });

        var response = handler.handleUnexpected(exception, request);

        assertNotNull(response.getBody(), "The error response must include its generic body");
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode(), "Unexpected failures must be HTTP 500");
        assertEquals("Internal server error.", response.getBody().message(), "Internal diagnostics must not reach clients");
        assertFalse(response.getBody().message().contains("sensitive"), "The client response must omit exception details");
        assertEquals(1, appender.list.size(), "The failure should produce exactly one server diagnostic");
        var event = appender.list.getFirst();
        assertEquals(ch.qos.logback.classic.Level.ERROR, event.getLevel(), "Unexpected failures must be logged at ERROR");
        assertEquals("Unexpected server exception exceptionClass=java.lang.IllegalStateException "
                        + "causeClasses=java.lang.IllegalStateException -> java.sql.SQLException.",
                event.getFormattedMessage(), "The safe diagnostic must identify the failure and full cause types");
        assertNotNull(event.getThrowableProxy(), "The safe diagnostic must contain a stack trace");
        assertEquals(originalStack, event.getThrowableProxy().getStackTraceElementProxyArray()[0].getStackTraceElement(),
                "The diagnostic must preserve the original failure location");
        assertNotNull(event.getThrowableProxy().getCause(), "The diagnostic must retain the cause chain");
        assertTrue(event.getThrowableProxy().getCause().getMessage().contains("java.sql.SQLException"),
                "The sanitized cause must retain its exception type");
        String diagnostic = event.getFormattedMessage() + event.getThrowableProxy().getMessage()
                + event.getThrowableProxy().getCause().getMessage();
        assertFalse(diagnostic.contains("hunter2"), "Passwords must not be logged");
        assertFalse(diagnostic.contains("token=secret"), "Tokens must not be logged");
        assertFalse(diagnostic.contains("rememberedCredential=opaque"), "Remembered credentials must not be logged");
        assertFalse(diagnostic.contains("body={secret}"), "Request or response bodies must not be logged");
    }

    private static void assertSanitizedBadRequest(
            org.springframework.http.HttpStatusCode status,
            int bodyStatus,
            String message,
            String path,
            String requestUri,
            String internalMessage) {
        assertEquals(HttpStatus.BAD_REQUEST, status, "Request-binding failures must remain HTTP 400");
        assertEquals(400, bodyStatus, "The response body must retain the HTTP 400 status");
        assertEquals("Invalid request.", message, "The client error message must remain sanitized");
        assertEquals(requestUri, path, "The response should retain the request path");
        assertFalse(message.contains(internalMessage), "Internal exception details must not reach the client");
    }

    private void assertDiagnosticLog(Exception exception, String requestUri) {
        assertEquals(1, appender.list.size(), "The handled failure should produce exactly one diagnostic log event");
        var event = appender.list.getFirst();
        assertEquals(ch.qos.logback.classic.Level.ERROR, event.getLevel(), "The diagnostic must be logged at ERROR");
        assertEquals(
                "Invalid request exceptionClass=" + exception.getClass().getName() + " requestUri=" + requestUri + ".",
                event.getFormattedMessage(),
                "The diagnostic must identify the exception class and request URI");
        assertNotNull(event.getThrowableProxy(), "The diagnostic must include the throwable stack trace");
        assertSame(exception, event.getThrowableProxy() instanceof ch.qos.logback.classic.spi.ThrowableProxy proxy
                ? proxy.getThrowable()
                : null, "The complete handled throwable must be attached to the diagnostic");
    }
}
