package com.shale.core.util;

import java.util.Locale;

/**
 * Central toggle and thresholds for Shale PERF instrumentation.
 *
 * PERF logging is on by default only for dev/local environments and can be
 * explicitly controlled with -DSHALE_PERF_LOGGING=true|false or the matching
 * environment variable. Slow warnings stay available independently so production
 * logs keep coarse signals without repeated diagnostic chatter.
 */
public final class PerformanceLogging {
    public static final String ENABLED_KEY = "SHALE_PERF_LOGGING";
    public static final String THRESHOLD_KEY = "SHALE_PERF_SLOW_THRESHOLD_MS";
    public static final long DEFAULT_SLOW_THRESHOLD_MS = 500L;

    private PerformanceLogging() {
    }

    public static long start() {
        return System.nanoTime();
    }

    public static long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }

    public static boolean isEnabled() {
        String explicit = setting(ENABLED_KEY);
        if (explicit != null && !explicit.isBlank()) {
            return Boolean.parseBoolean(explicit.trim());
        }
        String appEnv = setting("APP_ENV");
        if (appEnv == null || appEnv.isBlank()) {
            return true;
        }
        String normalized = appEnv.trim().toLowerCase(Locale.ROOT);
        return normalized.equals("dev") || normalized.equals("local") || normalized.equals("development");
    }

    public static long slowThresholdMs() {
        String configured = setting(THRESHOLD_KEY);
        if (configured == null || configured.isBlank()) {
            return DEFAULT_SLOW_THRESHOLD_MS;
        }
        try {
            return Math.max(0L, Long.parseLong(configured.trim()));
        } catch (NumberFormatException ignored) {
            return DEFAULT_SLOW_THRESHOLD_MS;
        }
    }

    public static boolean isSlow(long elapsedMs) {
        return elapsedMs >= slowThresholdMs();
    }

    public static boolean shouldLogNormal() {
        return isEnabled();
    }

    public static boolean shouldLogElapsed(long elapsedMs) {
        return isEnabled() || isSlow(elapsedMs);
    }

    private static String setting(String key) {
        String property = System.getProperty(key);
        if (property != null) {
            return property;
        }
        return System.getenv(key);
    }
}
