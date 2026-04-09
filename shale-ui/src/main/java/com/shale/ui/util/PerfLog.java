package com.shale.ui.util;

public final class PerfLog {

    private PerfLog() {
    }

    public static long start() {
        return System.nanoTime();
    }

    public static long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }

    public static void log(String area, String phase, String fields) {
        String suffix = (fields == null || fields.isBlank()) ? "" : " " + fields.trim();
        System.out.println("PERF " + area + " " + phase + suffix);
    }

    public static void logDone(String area, String fields, long startNanos) {
        long elapsedMs = elapsedMs(startNanos);
        String suffix = (fields == null || fields.isBlank()) ? "" : " " + fields.trim();
        System.out.println("PERF " + area + " done" + suffix + " elapsedMs=" + elapsedMs);
    }
}
