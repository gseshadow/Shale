package com.shale.updater.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
	void restartAppFailsWhenBinaryMissing() {
		MacPlatformSupport platformSupport = new MacPlatformSupport() {
			@Override
			Path relaunchTargetPath() {
				return Path.of("/definitely/missing/Shale.app");
			}
		};

		assertThrows(IOException.class, () -> platformSupport.restartApp(Path.of("/ignored"), "1.0.99"));
	}
}