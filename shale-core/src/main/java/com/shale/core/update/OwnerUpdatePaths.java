package com.shale.core.update;

import java.nio.file.Path;

/** Pure owner-correct paths. It never consults the executing process environment or loads a profile. */
public record OwnerUpdatePaths(Path supportRoot, Path attemptStore, Path executionLock, Path evidenceLogs,
		Path updaterExecutable, Path versionMetadata) {
	public static OwnerUpdatePaths from(WindowsInstallationRegistration registration) {
		Path support = registration.supportRoot();
		Path install = registration.installRoot();
		return new OwnerUpdatePaths(support, support.resolve("update-attempts"),
				UpdateExecutionLock.path(support), support.resolve("logs").resolve("updates"),
				install.resolve("app").resolve("updater").resolve("ShaleUpdater.exe"),
				install.resolve("app").resolve(InstalledVersionMetadata.FILE_NAME));
	}
}
