package com.shale.desktop;

/** Plain JVM entry point used by non-modular jpackage launchers. */
public final class ShaleLauncher {

	private ShaleLauncher() {
	}

	public static void main(String[] args) {
		MainApp.main(args);
	}
}
