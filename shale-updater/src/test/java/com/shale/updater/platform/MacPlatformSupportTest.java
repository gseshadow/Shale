package com.shale.updater.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;

final class MacPlatformSupportTest {

	@Test
	void restartAppUsesApplicationsTargetAndVersionedJar() throws Exception {
		Path installDir = Path.of("/Applications/Shale.app");
		RecordingMacPlatformSupport platformSupport = new RecordingMacPlatformSupport();

		platformSupport.restartApp(installDir, "1.0.99");

		assertEquals(Path.of("/Applications/Shale.app"), platformSupport.targetApp);
		assertEquals("1.0.99", platformSupport.expectedVersion);
		assertEquals(Path.of("/Applications/Shale.app/Contents/app/shale-desktop-1.0.99.jar"), platformSupport.expectedMarker);
		assertEquals(Path.of("/Applications/Shale.app/Contents/app/lib/shale-updater-1.0.99.jar"), platformSupport.expectedUpdaterJar);
	}

	@Test
	void helperScriptLogsPollingStateAndFinalOpenCommand() {
		MacPlatformSupport platformSupport = new MacPlatformSupport();
		String script = platformSupport.helperScript(
				Path.of("/Applications/Shale.app"),
				"2.0.0",
				Path.of("/Applications/Shale.app/Contents/app/shale-desktop-2.0.0.jar"),
				Path.of("/Applications/Shale.app/Contents/app/lib/shale-updater-2.0.0.jar"),
				1234L,
				Path.of("/tmp/updater-output.log"));

		assertTrue(script.contains("helper target path: $TARGET_APP"));
		assertTrue(script.contains("poll attempt=$attempt"));
		assertTrue(script.contains("expected_version=$EXPECTED_VERSION"));
		assertTrue(script.contains("expected_marker_path=$EXPECTED_MARKER"));
		assertTrue(script.contains("expected_marker_exists=$marker_exists"));
		assertTrue(script.contains("expected_updater_jar_exists=$updater_jar_exists"));
		assertTrue(script.contains("helper started"));
		assertTrue(script.contains("helper arguments: $*"));
		assertTrue(script.contains("helper expected version: $EXPECTED_VERSION"));
		assertTrue(script.contains("helper expected marker path: $EXPECTED_MARKER"));
		assertTrue(script.contains("helper relaunch working directory: $RELAUNCH_WORKDIR"));
		assertTrue(script.contains("final relaunch command: $OPEN_CMD"));
		assertTrue(script.contains("if cd \"$RELAUNCH_WORKDIR\"; then"));
		assertTrue(script.contains("relaunch cwd set explicitly: $RELAUNCH_WORKDIR"));
		assertTrue(script.contains("open stdout/stderr: $open_output"));
		assertTrue(script.contains("open exit code: $open_exit"));
		assertTrue(script.contains("helper finished"));
	}

	@Test
	void expectedUpdaterJarPathIsOptionalWhenVersionUnknown() {
		MacPlatformSupport platformSupport = new MacPlatformSupport();
		Path contentsAppDir = Path.of("/Applications/Shale.app/Contents/app");

		assertNull(platformSupport.expectedUpdaterJarPath(contentsAppDir, null));
		assertNull(platformSupport.expectedUpdaterJarPath(contentsAppDir, ""));
	}

	private static final class RecordingMacPlatformSupport extends MacPlatformSupport {
		private Path targetApp;
		private String expectedVersion;
		private Path expectedMarker;
		private Path expectedUpdaterJar;

		@Override
		void startRelaunchHelper(
				Path targetApp,
				String expectedVersion,
				Path expectedMarker,
				Path expectedUpdaterJar,
				long updaterPid,
				Path helperLogPath) {
			this.targetApp = targetApp;
			this.expectedVersion = expectedVersion;
			this.expectedMarker = expectedMarker;
			this.expectedUpdaterJar = expectedUpdaterJar;
		}
	}
}
