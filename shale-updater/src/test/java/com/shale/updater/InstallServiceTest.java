package com.shale.updater;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class InstallServiceTest {

	private final InstallService installService = new InstallService();

	@Test
	void replaceInstallDirCopiesMacAppBundleWithoutDitto(@TempDir Path tempDir) throws IOException {
		Path sourceApp = tempDir.resolve("staged").resolve("Shale.app");
		Path sourceLauncher = sourceApp.resolve("Contents").resolve("MacOS").resolve("Shale");
		Path sourceConfig = sourceApp.resolve("Contents").resolve("Resources").resolve("config.json");
		Path sourceRuntimeJava = sourceApp.resolve("Contents").resolve("runtime").resolve("Contents").resolve("Home")
				.resolve("bin").resolve("java");
		Path sourceRuntimeJar = sourceApp.resolve("Contents").resolve("runtime").resolve("Contents").resolve("Home")
				.resolve("bin").resolve("jar");
		Path sourceJspawnhelper = sourceApp.resolve("Contents").resolve("runtime").resolve("Contents").resolve("Home")
				.resolve("lib").resolve("jspawnhelper");
		Path installApp = tempDir.resolve("Applications").resolve("Shale.app");
		Path oldFile = installApp.resolve("Contents").resolve("obsolete.txt");

		Files.createDirectories(sourceLauncher.getParent());
		Files.createDirectories(sourceConfig.getParent());
		Files.createDirectories(sourceRuntimeJava.getParent());
		Files.createDirectories(sourceRuntimeJar.getParent());
		Files.createDirectories(sourceJspawnhelper.getParent());
		Files.writeString(sourceLauncher, "launcher");
		Files.writeString(sourceConfig, "config");
		Files.writeString(sourceRuntimeJava, "java");
		Files.writeString(sourceRuntimeJar, "jar");
		Files.writeString(sourceJspawnhelper, "jspawnhelper");
		Files.createDirectories(oldFile.getParent());
		Files.writeString(oldFile, "old");
		removeExecutable(sourceLauncher);
		removeExecutable(sourceRuntimeJava);
		removeExecutable(sourceRuntimeJar);
		removeExecutable(sourceJspawnhelper);

		installService.replaceInstallDir(sourceApp, installApp);

		assertTrue(Files.exists(installApp.resolve("Contents").resolve("MacOS").resolve("Shale")));
		assertEquals("launcher", Files.readString(installApp.resolve("Contents").resolve("MacOS").resolve("Shale")));
		assertEquals("config", Files.readString(installApp.resolve("Contents").resolve("Resources").resolve("config.json")));
		assertEquals("java", Files.readString(installApp.resolve("Contents").resolve("runtime").resolve("Contents")
				.resolve("Home").resolve("bin").resolve("java")));
		assertTrue(Files.notExists(oldFile));
		assertTrue(Files.isExecutable(installApp.resolve("Contents").resolve("MacOS").resolve("Shale")));
		assertTrue(Files.isExecutable(installApp.resolve("Contents").resolve("runtime").resolve("Contents")
				.resolve("Home").resolve("bin").resolve("java")));
		assertTrue(Files.isExecutable(installApp.resolve("Contents").resolve("runtime").resolve("Contents")
				.resolve("Home").resolve("bin").resolve("jar")));
		assertTrue(Files.isExecutable(installApp.resolve("Contents").resolve("runtime").resolve("Contents")
				.resolve("Home").resolve("lib").resolve("jspawnhelper")));
	}

	@Test
	void replaceInstallDirOverwritesExistingMacFiles(@TempDir Path tempDir) throws IOException {
		Path sourceApp = tempDir.resolve("stage").resolve("Shale.app");
		Path targetApp = tempDir.resolve("Applications").resolve("Shale.app");
		Path sourceInfo = sourceApp.resolve("Contents").resolve("Info.plist");
		Path targetInfo = targetApp.resolve("Contents").resolve("Info.plist");

		Files.createDirectories(sourceInfo.getParent());
		Files.writeString(sourceInfo, "new-info");
		Files.createDirectories(targetInfo.getParent());
		Files.writeString(targetInfo, "old-info");

		installService.replaceInstallDir(sourceApp, targetApp);

		assertEquals("new-info", Files.readString(targetInfo));
	}

	private void removeExecutable(Path path) throws IOException {
		try {
			Set<PosixFilePermission> permissions = Set.copyOf(Files.getPosixFilePermissions(path));
			permissions = new java.util.HashSet<>(permissions);
			permissions.remove(PosixFilePermission.OWNER_EXECUTE);
			permissions.remove(PosixFilePermission.GROUP_EXECUTE);
			permissions.remove(PosixFilePermission.OTHERS_EXECUTE);
			Files.setPosixFilePermissions(path, permissions);
		} catch (UnsupportedOperationException ex) {
			// Best-effort only on non-POSIX filesystems like Windows.
			path.toFile().setExecutable(false, false);
		}
	}
}
