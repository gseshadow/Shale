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
		assertEquals(Path.of("/Applications/Shale.app/Contents/app"), platformSupport.contentsAppDir);
		assertEquals(Path.of("/Applications/Shale.app/Contents/app/shale-desktop-1.0.99.jar"), platformSupport.expectedJar);
		assertEquals(Path.of("/Applications/Shale.app/Contents/app/lib/shale-updater-1.0.99.jar"), platformSupport.expectedUpdaterJar);
	}

	@Test
	void helperScriptLogsPollingStateAndFinalOpenCommand() {
		MacPlatformSupport platformSupport = new MacPlatformSupport();
		String script = platformSupport.helperScript(
				Path.of("/Applications/Shale.app"),
				Path.of("/Applications/Shale.app/Contents/app"),
				Path.of("/Applications/Shale.app/Contents/app/shale-desktop-2.0.0.jar"),
				Path.of("/Applications/Shale.app/Contents/app/lib/shale-updater-2.0.0.jar"),
				1234L,
				Path.of("/tmp/updater-output.log"));

		assertTrue(script.contains("helper target path: $TARGET_APP"));
		assertTrue(script.contains("poll attempt=$attempt"));
		assertTrue(script.contains("app_exists=$app_exists"));
		assertTrue(script.contains("contents_app_exists=$contents_exists"));
		assertTrue(script.contains("expected_jar_exists=$jar_exists"));
		assertTrue(script.contains("expected_updater_jar_exists=$updater_jar_exists"));
		assertTrue(script.contains("helper started"));
		assertTrue(script.contains("helper arguments: $*"));
		assertTrue(script.contains("final relaunch command: $OPEN_CMD"));
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
		private Path contentsAppDir;
		private Path expectedJar;
		private Path expectedUpdaterJar;

		@Override
		void startRelaunchHelper(
				Path targetApp,
				Path contentsAppDir,
				Path expectedJar,
				Path expectedUpdaterJar,
				long updaterPid,
				Path helperLogPath) {
			this.targetApp = targetApp;
			this.contentsAppDir = contentsAppDir;
			this.expectedJar = expectedJar;
			this.expectedUpdaterJar = expectedUpdaterJar;
		}
	}
}
