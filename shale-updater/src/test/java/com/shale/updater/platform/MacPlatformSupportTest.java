package com.shale.updater.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
	}

	@Test
	void helperScriptLogsPollingStateAndFinalOpenCommand() {
		MacPlatformSupport platformSupport = new MacPlatformSupport();
		String script = platformSupport.helperScript(
				Path.of("/Applications/Shale.app"),
				Path.of("/Applications/Shale.app/Contents/app"),
				Path.of("/Applications/Shale.app/Contents/app/shale-desktop-2.0.0.jar"),
				1234L,
				Path.of("/tmp/updater-output.log"));

		assertTrue(script.contains("helper target path: $TARGET_APP"));
		assertTrue(script.contains("poll attempt=$attempt"));
		assertTrue(script.contains("app_exists=$app_exists"));
		assertTrue(script.contains("contents_app_exists=$contents_exists"));
		assertTrue(script.contains("expected_jar_exists=$jar_exists"));
		assertTrue(script.contains("final relaunch command: $RELAUNCH_CMD"));
		assertTrue(script.contains("open \"$TARGET_APP\""));
	}

	private static final class RecordingMacPlatformSupport extends MacPlatformSupport {
		private Path targetApp;
		private Path contentsAppDir;
		private Path expectedJar;

		@Override
		void startRelaunchHelper(Path targetApp, Path contentsAppDir, Path expectedJar, long updaterPid, Path helperLogPath) {
			this.targetApp = targetApp;
			this.contentsAppDir = contentsAppDir;
			this.expectedJar = expectedJar;
		}
	}
}
