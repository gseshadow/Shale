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
		Path installApp = tempDir.resolve("Applications").resolve("Shale.app");
		Path oldFile = installApp.resolve("Contents").resolve("obsolete.txt");

		Files.createDirectories(sourceLauncher.getParent());
		Files.createDirectories(sourceConfig.getParent());
		Files.writeString(sourceLauncher, "launcher");
		Files.writeString(sourceConfig, "config");
		Files.createDirectories(oldFile.getParent());
		Files.writeString(oldFile, "old");
		setExecutable(sourceLauncher);

		installService.replaceInstallDir(sourceApp, installApp);

		assertTrue(Files.exists(installApp.resolve("Contents").resolve("MacOS").resolve("Shale")));
		assertEquals("launcher", Files.readString(installApp.resolve("Contents").resolve("MacOS").resolve("Shale")));
		assertEquals("config", Files.readString(installApp.resolve("Contents").resolve("Resources").resolve("config.json")));
		assertTrue(Files.notExists(oldFile));
		assertTrue(Files.isExecutable(installApp.resolve("Contents").resolve("MacOS").resolve("Shale")));
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

	private void setExecutable(Path path) throws IOException {
		try {
			Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(path);
			permissions.add(PosixFilePermission.OWNER_EXECUTE);
			permissions.add(PosixFilePermission.GROUP_EXECUTE);
			permissions.add(PosixFilePermission.OTHERS_EXECUTE);
			Files.setPosixFilePermissions(path, permissions);
		} catch (UnsupportedOperationException ex) {
			assertTrue(path.toFile().setExecutable(true, false));
		}
	}
}
