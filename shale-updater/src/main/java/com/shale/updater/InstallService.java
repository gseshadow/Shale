package com.shale.updater;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.StandardCopyOption;
import java.util.EnumSet;
import java.util.Set;

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

	public void replaceInstallDir(Path sourceDir, Path installDir) throws IOException {
		deleteRecursively(installDir);
		if (isMacAppBundle(sourceDir, installDir)) {
			copyMacAppBundle(sourceDir, installDir);
			ensureMacLauncherExecutable(installDir);
			return;
		}
		copyDirectory(sourceDir, installDir);
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

	private void copyDirectory(Path source, Path target) throws IOException {
		Files.createDirectories(target);

		try (var stream = Files.walk(source)) {
			stream.forEach(src ->
			{
				try {
					Path relative = source.relativize(src);
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

	private void copyMacAppBundle(Path source, Path target) throws IOException {
		ProcessBuilder processBuilder = new ProcessBuilder("/usr/bin/ditto", source.toString(), target.toString());
		System.out.println("Running macOS app bundle copy command: " + processBuilder.command());
		System.out.println("ditto source: " + source);
		System.out.println("ditto destination: " + target);

		Process process;
		try {
			process = processBuilder.inheritIO().start();
		} catch (IOException ex) {
			System.out.println("ditto launch failed: " + ex.getMessage());
			throw new IOException("Failed to launch ditto for macOS app bundle copy", ex);
		}

		try {
			int exit = process.waitFor();
			System.out.println("ditto exit code: " + exit);
			if (exit != 0) {
				throw new IOException("ditto failed with exit code " + exit);
			}
		} catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			System.out.println("ditto interrupted: " + ex.getMessage());
			throw new IOException("Interrupted while copying macOS app bundle", ex);
		}
	}

	private void ensureMacLauncherExecutable(Path installDir) throws IOException {
		Path macOsDir = installDir.resolve("Contents").resolve("MacOS");
		if (!Files.isDirectory(macOsDir)) {
			return;
		}

		try (var stream = Files.list(macOsDir)) {
			stream.filter(Files::isRegularFile)
					.forEach(path -> {
						try {
							setExecutable(path);
						} catch (IOException ex) {
							throw new RuntimeException(ex);
						}
					});
		} catch (RuntimeException ex) {
			if (ex.getCause() instanceof IOException io) {
				throw io;
			}
			throw ex;
		}
	}

	private void setExecutable(Path path) throws IOException {
		try {
			Set<PosixFilePermission> permissions = Files.exists(path)
					? Files.getPosixFilePermissions(path)
					: EnumSet.noneOf(PosixFilePermission.class);
			permissions = EnumSet.copyOf(permissions);
			permissions.add(PosixFilePermission.OWNER_EXECUTE);
			permissions.add(PosixFilePermission.GROUP_EXECUTE);
			permissions.add(PosixFilePermission.OTHERS_EXECUTE);
			Files.setPosixFilePermissions(path, permissions);
		} catch (UnsupportedOperationException ex) {
			if (!path.toFile().setExecutable(true, false)) {
				throw new IOException("Failed to set executable bit on " + path);
			}
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

	private boolean isMacAppBundle(Path sourceDir, Path installDir) {
		return sourceDir != null
				&& installDir != null
				&& sourceDir.getFileName() != null
				&& installDir.getFileName() != null
				&& sourceDir.getFileName().toString().endsWith(".app")
				&& installDir.getFileName().toString().endsWith(".app");
	}
}
