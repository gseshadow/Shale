package com.shale.desktop.update;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
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
		Path javaBinary = installDir.resolve("Contents")
				.resolve("runtime")
				.resolve("Contents")
				.resolve("Home")
				.resolve("bin")
				.resolve("java");
		Files.createDirectories(javaBinary.getParent());
		Files.writeString(javaBinary, "java");

		assertEquals(javaBinary, DesktopUpdateLauncher.resolveMacJavaBinary(installDir));
		assertTrue(Files.exists(DesktopUpdateLauncher.resolveMacJavaBinary(installDir)));
	}

	@Test
	void buildMacLaunchCommandUsesDetachedShellHelper(@TempDir Path tempDir) throws IOException {
		Path installDir = tempDir.resolve("Shale.app");
		Path javaBinary = installDir.resolve("Contents")
				.resolve("runtime")
				.resolve("Contents")
				.resolve("Home")
				.resolve("bin")
				.resolve("java");
		Path libDir = installDir.resolve("Contents").resolve("app").resolve("lib");
		Path updaterJar = libDir.resolve("shale-updater-1.0.14.jar");
		Path updaterLog = tempDir.resolve("updater-output.log");

		Files.createDirectories(javaBinary.getParent());
		Files.createDirectories(libDir);
		Files.writeString(javaBinary, "java");
		Files.writeString(updaterJar, "jar");

		DesktopUpdateLauncher.LaunchPlan launchPlan = DesktopUpdateLauncher.buildMacLaunchCommand(installDir, "1.0.13", updaterLog);

		ProcessBuilder pb = launchPlan.processBuilder();
		assertEquals(List.of("/bin/sh", launchPlan.macHelperScript().toString()), pb.command());
		assertEquals(Path.of("/").toFile(), pb.directory());
		assertEquals(Path.of("/"), launchPlan.helperWorkingDirectory());

		String helperScript = Files.readString(launchPlan.macHelperScript(), StandardCharsets.UTF_8);

		assertTrue(helperScript.contains("cd '/'"));
		assertTrue(helperScript.contains("nohup "));
		assertTrue(helperScript.contains("'--currentVersion' '1.0.13'"));
		assertTrue(helperScript.contains("'--installDir' '" + shellPath(installDir) + "'"));
		assertTrue(helperScript.contains(shellPath(updaterLog)));
		assertTrue(helperScript.contains("2>&1 < /dev/null &"));
	}

	private static String shellPath(Path path) {
		return path.toString().replace('\\', '/');
	}
}