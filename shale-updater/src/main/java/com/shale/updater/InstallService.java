package com.shale.updater;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class InstallService {

	public Path backupInstallDir(Path installDir) throws IOException {
		Path backupDir = installDir.getParent().resolve(installDir.getFileName() + "-backup");

		if (Files.exists(backupDir)) {
			deleteRecursively(backupDir);
		}

		copyDirectoryExcludingUpdater(installDir, backupDir);
		return backupDir;
	}

	public void applyStagedUpdate(Path stagingDir, Path installDir) throws IOException {
		copyDirectoryExcludingUpdater(stagingDir, installDir);
	}

	private void copyDirectoryExcludingUpdater(Path source, Path target) throws IOException {
		Files.createDirectories(target);

		try (var stream = Files.walk(source)) {
			stream.forEach(src ->
			{
				try {
					Path relative = source.relativize(src);

					if (shouldSkip(relative)) {
						return;
					}

					Path dest = target.resolve(relative);

					if (Files.isDirectory(src)) {
						Files.createDirectories(dest);
					} else {
						Path parent = dest.getParent();
						if (parent != null) {
							Files.createDirectories(parent);
						}
						Files.copy(src, dest, StandardCopyOption.REPLACE_EXISTING);
					}
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
			});
		}
	}

	private void deleteRecursively(Path path) throws IOException {
		if (!Files.exists(path)) {
			return;
		}

		try (var stream = Files.walk(path)) {
			stream.sorted((a, b) -> b.getNameCount() - a.getNameCount())
					.forEach(p ->
					{
						try {
							Files.deleteIfExists(p);
						} catch (IOException e) {
							throw new RuntimeException(e);
						}
					});
		}
	}

	private boolean shouldSkip(Path relative) {
		if (relative == null || relative.getNameCount() == 0) {
			return false;
		}

		String normalized = relative.toString().replace('\\', '/');

		return normalized.equals("app/updater")
				|| normalized.startsWith("app/updater/")
				|| normalized.equals("updater")
				|| normalized.startsWith("updater/");
	}
}