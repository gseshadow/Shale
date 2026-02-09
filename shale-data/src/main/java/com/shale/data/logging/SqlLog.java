package com.shale.data.logging;

public final class SqlLog {
	private SqlLog() {
	}

	public static long now() {
		return System.nanoTime();
	}

	public static void slow(long startedNs, String label, long thresholdMs) {
		long ms = (System.nanoTime() - startedNs) / 1_000_000L;
		if (ms >= thresholdMs) {
			System.getLogger("SQL").log(System.Logger.Level.INFO, () -> label + " took " + ms + "ms");
		}
	}
}
