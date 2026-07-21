package com.shale.ui.util;

import com.shale.core.util.PerformanceLogging;
import org.slf4j.Logger;

public final class PerfLog {

    private PerfLog() {
    }

    public static long start() {
        return PerformanceLogging.start();
    }

    public static long elapsedMs(long startNanos) {
        return PerformanceLogging.elapsedMs(startNanos);
    }

    public static boolean isEnabled() {
        return PerformanceLogging.isEnabled();
    }

    public static long slowThresholdMs() {
        return PerformanceLogging.slowThresholdMs();
    }

    public static boolean isSlow(long elapsedMs) {
        return PerformanceLogging.isSlow(elapsedMs);
    }

    public static void log(String area, String phase, String fields) {
        if (!isEnabled()) {
            return;
        }
        String suffix = (fields == null || fields.isBlank()) ? "" : " " + fields.trim();
        System.getLogger("PERF").log(System.Logger.Level.DEBUG, "PERF " + area + " " + phase + suffix);
    }

    public static void logDone(String area, String fields, long startNanos) {
        long elapsedMs = elapsedMs(startNanos);
        logElapsed(area, "done", fields, elapsedMs);
    }

    public static void logElapsed(String area, String phase, String fields, long elapsedMs) {
        if (!PerformanceLogging.shouldLogElapsed(elapsedMs)) {
            return;
        }
        String suffix = (fields == null || fields.isBlank()) ? "" : " " + fields.trim();
        System.Logger.Level level = switch (PerformanceLogging.levelForElapsed(elapsedMs)) {
            case WARN, ERROR -> System.Logger.Level.WARNING;
            case INFO -> System.Logger.Level.INFO;
            case DEBUG -> System.Logger.Level.DEBUG;
        };
        System.getLogger("PERF").log(level, "PERF " + area + " " + phase + suffix + " elapsedMs=" + elapsedMs);
    }

    public static void debug(Logger log, String message, Object... args) {
        if (isEnabled()) {
            log.debug(message, args);
        }
    }

    public static void elapsed(Logger log, long startNanos, String message, Object... args) {
        long elapsedMs = elapsedMs(startNanos);
        switch (PerformanceLogging.levelForElapsed(elapsedMs)) {
            case WARN, ERROR -> log.warn(message, append(args, elapsedMs));
            case INFO -> log.info(message, append(args, elapsedMs));
            case DEBUG -> {
                if (isEnabled()) {
                    log.debug(message, append(args, elapsedMs));
                }
            }
        }
    }

    private static Object[] append(Object[] args, Object... tail) {
        Object[] safeArgs = args == null ? new Object[0] : args;
        Object[] out = new Object[safeArgs.length + tail.length];
        System.arraycopy(safeArgs, 0, out, 0, safeArgs.length);
        System.arraycopy(tail, 0, out, safeArgs.length, tail.length);
        return out;
    }
}
