package com.shale.data.dao;

import com.shale.core.util.PerformanceLogging;
import org.slf4j.Logger;
import org.slf4j.event.Level;

/** Closed phase/provider labels and numeric measurements only; never pass search or record values. */
public final class SearchPerformance {
    private static final ThreadLocal<Long> REQUEST = ThreadLocal.withInitial(() -> 0L);
    private SearchPerformance() { }

    public static <T> T withRequest(long request, java.util.function.Supplier<T> work) {
        long previous = REQUEST.get();
        REQUEST.set(request);
        try { return work.get(); }
        finally { if (previous == 0) REQUEST.remove(); else REQUEST.set(previous); }
    }

    public static void log(Logger log, String phase, long request, String provider, int rows, long elapsedMs) {
        if (PerformanceLogging.shouldLogElapsed(elapsedMs))
            log.atLevel(Level.valueOf(PerformanceLogging.levelForElapsed(elapsedMs).name()))
                    .log("PERF search phase={} request={} provider={} rows={} elapsedMs={}",
                            phase, request == 0 ? REQUEST.get() : request, provider, rows, elapsedMs);
    }
}
