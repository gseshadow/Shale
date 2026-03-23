package com.shale.desktop.update;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class DesktopUpdateLauncherTest {

	@Test
	void resolveMacUpdaterJarUsesPackagedLibDirectory(@TempDir Path tempDir) throws IOException {
		Path installDir = tempDir.resolve("Shale.app");
		Path libDir = installDir.resolve("Contents").resolve("app").resolve("lib");
		Files.createDirectories(libDir);
		Path updaterJar = libDir.resolve("shale-updater-1.0.14.jar");
		Files.writeString(updaterJar, "jar");

		assertEquals(updaterJar, DesktopUpdateLauncher.resolveMacUpdaterJar(installDir));
	}

	@Test
	void resolveMacJavaBinaryPrefersBundledRuntime(@TempDir Path tempDir) throws IOException {
		Path installDir = tempDir.resolve("Shale.app");
		Path javaBinary = installDir.resolve("Contents").resolve("runtime").resolve("Contents").resolve("Home").resolve("bin").resolve("java");
		Files.createDirectories(javaBinary.getParent());
		Files.writeString(javaBinary, "java");

		assertEquals(javaBinary, DesktopUpdateLauncher.resolveMacJavaBinary(installDir));
		assertTrue(Files.exists(DesktopUpdateLauncher.resolveMacJavaBinary(installDir)));
	}

	@Test
	void buildMacLaunchCommandUsesSelfContainedUpdaterJar(@TempDir Path tempDir) throws IOException {
		Path installDir = tempDir.resolve("Shale.app");
		Path javaBinary = installDir.resolve("Contents").resolve("runtime").resolve("Contents").resolve("Home").resolve("bin").resolve("java");
		Path libDir = installDir.resolve("Contents").resolve("app").resolve("lib");
		Path updaterJar = libDir.resolve("shale-updater-1.0.14.jar");
		Path updaterLog = tempDir.resolve("updater-output.log");

		Files.createDirectories(javaBinary.getParent());
		Files.createDirectories(libDir);
		Files.writeString(javaBinary, "java");
		Files.writeString(updaterJar, "jar");

		ProcessBuilder pb = DesktopUpdateLauncher.buildMacLaunchCommand(installDir, "1.0.13", updaterLog);

		assertEquals(List.of(
				javaBinary.toString(),
				"-jar",
				updaterJar.toString(),
				"--currentVersion", "1.0.13",
				"--installDir", installDir.toString()), pb.command());
		assertEquals(installDir.toFile(), pb.directory());
	}
}
