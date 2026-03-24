package com.shale.updater.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class MacPlatformSupportTest {

	@Test
	void relaunchTargetPathUsesApplicationsFolder() {
		MacPlatformSupport platformSupport = new MacPlatformSupport();
		assertEquals(Path.of("/Applications/Shale.app"), platformSupport.relaunchTargetPath());
	}

	@Test
	void resolveStagedInstallDirFindsDirectAppBundle(@TempDir Path tempDir) throws Exception {
		MacPlatformSupport platformSupport = new MacPlatformSupport();
		Path appBundle = tempDir.resolve("Shale.app");
		Files.createDirectories(appBundle);

		assertEquals(appBundle, platformSupport.resolveStagedInstallDir(tempDir));
	}

	@Test
	void restartAppFailsWhenHelperWasNotArmed() {
		MacPlatformSupport platformSupport = new MacPlatformSupport();
		assertThrows(IOException.class, () -> platformSupport.restartApp(Path.of("/ignored"), "1.0.99"));
	}

	@Test
	void helperScriptWaitsForExpectedVersionedJars() {
		MacPlatformSupport platformSupport = new MacPlatformSupport();
		String script = platformSupport.helperScript(
				123L,
				Path.of("/Applications/Shale.app/Contents/app/shale-desktop-1.0.99.jar"),
				Path.of("/Applications/Shale.app/Contents/app/lib/shale-updater-1.0.99.jar"),
				Path.of("/Applications/Shale.app/Contents/MacOS/Shale"),
				Path.of("/tmp/helper.log"));

		assertTrue(script.contains("while kill -0 123"));
		assertTrue(script.contains("shale-desktop-1.0.99.jar"));
		assertTrue(script.contains("shale-updater-1.0.99.jar"));
		assertTrue(script.contains("chosen relaunch working directory: '/'"));
		assertTrue(script.contains("if cd '/'"));
		assertTrue(script.contains("cwd set explicitly to '/'"));
		assertTrue(script.contains("final relaunch command: '/Applications/Shale.app/Contents/MacOS/Shale' >> \\\"$LOG_FILE\\\" 2>&1 &"));
		assertTrue(script.contains("relaunch command executed"));
	}
}
