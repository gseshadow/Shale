package com.shale.data.logging;

import com.shale.core.util.PerformanceLogging;

public final class SqlLog {
	private SqlLog() {
	}

	public static long now() {
		return PerformanceLogging.start();
	}

	public static void slow(long startedNs, String label, long thresholdMs) {
		long ms = PerformanceLogging.elapsedMs(startedNs);
		long effectiveThreshold = thresholdMs > 0 ? thresholdMs : PerformanceLogging.slowThresholdMs();
		if (ms >= effectiveThreshold) {
			System.getLogger("SQL").log(System.Logger.Level.WARNING,
					() -> label + " took " + ms + "ms thresholdMs=" + effectiveThreshold);
		} else if (PerformanceLogging.isEnabled()) {
			System.getLogger("SQL").log(System.Logger.Level.DEBUG, () -> label + " took " + ms + "ms");
		}
	}
}
