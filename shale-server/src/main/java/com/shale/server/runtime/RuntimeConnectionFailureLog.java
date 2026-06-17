package com.shale.server.runtime;

import java.util.regex.Pattern;

import org.slf4j.Logger;

final class RuntimeConnectionFailureLog {
    private static final Pattern PASSWORD_PARAMETER = Pattern.compile("(?i)\\b(password|pwd)\\s*=\\s*[^;\\s]+");
    private static final Pattern JDBC_URL = Pattern.compile("jdbc:[^\\s]+");

    private RuntimeConnectionFailureLog() {
    }

    static void log(Logger logger, String operation, Throwable failure) {
        Throwable root = rootCause(failure);
        logger.warn("{} failed: {}: {}", operation, root.getClass().getName(), sanitize(root.getMessage()));
    }

    private static Throwable rootCause(Throwable failure) {
        Throwable root = failure;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        return root;
    }

    private static String sanitize(String message) {
        if (message == null || message.isBlank()) {
            return "<no message>";
        }
        String sanitized = PASSWORD_PARAMETER.matcher(message).replaceAll("$1=<redacted>");
        return JDBC_URL.matcher(sanitized).replaceAll("jdbc:<redacted>");
    }
}
