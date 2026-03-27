package com.shale.updater;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
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
			ensureMacBundleExecutables(installDir);
			logMacRuntimeJavaStatus(installDir);
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
		log("Starting macOS app bundle copy");
		log("macOS app bundle source: " + source);
		log("macOS app bundle destination: " + target);

		try {
			copyRecursively(source, target);
			log("macOS app bundle copy succeeded");
		} catch (IOException ex) {
			log("macOS app bundle copy failed: " + stackTrace(ex));
			throw ex;
		}
	}

	private void copyRecursively(Path source, Path target) throws IOException {
		FileVisitor<Path> visitor = new SimpleFileVisitor<>() {
			@Override
			public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
				Path destinationDir = resolveDestination(source, target, dir);
				Files.createDirectories(destinationDir);
				copyPosixPermissions(dir, destinationDir);
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
				Path destinationFile = resolveDestination(source, target, file);
				Path parent = destinationFile.getParent();
				if (parent != null) {
					Files.createDirectories(parent);
				}
				Files.copy(
						file,
						destinationFile,
						LinkOption.NOFOLLOW_LINKS,
						StandardCopyOption.REPLACE_EXISTING,
						StandardCopyOption.COPY_ATTRIBUTES);
				copyPosixPermissions(file, destinationFile);
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
				if (exc != null) {
					throw exc;
				}
				Path destinationDir = resolveDestination(source, target, dir);
				Files.setLastModifiedTime(
						destinationDir,
						Files.getLastModifiedTime(dir, LinkOption.NOFOLLOW_LINKS));
				copyPosixPermissions(dir, destinationDir);
				return FileVisitResult.CONTINUE;
			}
		};

		Files.walkFileTree(source, visitor);
	}

	private Path resolveDestination(Path sourceRoot, Path targetRoot, Path current) {
		Path relative = sourceRoot.relativize(current);
		return relative.getNameCount() == 0 ? targetRoot : targetRoot.resolve(relative);
	}

	private void copyPosixPermissions(Path source, Path target) throws IOException {
		try {
			Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(source, LinkOption.NOFOLLOW_LINKS);
			Files.setPosixFilePermissions(target, permissions);
		} catch (UnsupportedOperationException ignored) {
			// Ignore when the filesystem does not expose POSIX permissions.
		}
	}

	private void ensureMacBundleExecutables(Path installDir) throws IOException {
		ensureMacLauncherExecutable(installDir);
		ensureMacRuntimeBinExecutables(installDir);
		ensureMacRuntimeLibExecutables(installDir);
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

	private void ensureMacRuntimeBinExecutables(Path installDir) throws IOException {
		Path runtimeBinDir = installDir.resolve("Contents")
				.resolve("runtime")
				.resolve("Contents")
				.resolve("Home")
				.resolve("bin");
		if (!Files.isDirectory(runtimeBinDir)) {
			return;
		}

		try (var stream = Files.list(runtimeBinDir)) {
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

	private void ensureMacRuntimeLibExecutables(Path installDir) throws IOException {
		Path runtimeLibDir = installDir.resolve("Contents")
				.resolve("runtime")
				.resolve("Contents")
				.resolve("Home")
				.resolve("lib");
		if (!Files.isDirectory(runtimeLibDir)) {
			return;
		}

		Path jspawnhelperPath = runtimeLibDir.resolve("jspawnhelper");
		setExecutableIfRegularFile(jspawnhelperPath);

		// Some runtime-native helpers under Home/lib are binary entrypoints without file extensions.
		// Restore execute bits to avoid regressing subprocess support after replacement.
		setExecutableIfRegularFile(runtimeLibDir.resolve("jexec"));
	}

	private void logMacRuntimeJavaStatus(Path installDir) {
		Path runtimeJavaPath = installDir.resolve("Contents")
				.resolve("runtime")
				.resolve("Contents")
				.resolve("Home")
				.resolve("bin")
				.resolve("java");
		boolean exists = Files.exists(runtimeJavaPath);
		boolean executable = exists && Files.isExecutable(runtimeJavaPath);
		log("macOS runtime java path: " + runtimeJavaPath + ", exists=" + exists + ", executable=" + executable);

		Path jspawnhelperPath = installDir.resolve("Contents")
				.resolve("runtime")
				.resolve("Contents")
				.resolve("Home")
				.resolve("lib")
				.resolve("jspawnhelper");
		boolean jspawnhelperExists = Files.exists(jspawnhelperPath);
		boolean jspawnhelperExecutable = jspawnhelperExists && Files.isExecutable(jspawnhelperPath);
		log("macOS runtime jspawnhelper path: " + jspawnhelperPath
				+ ", exists=" + jspawnhelperExists
				+ ", executable=" + jspawnhelperExecutable);
	}

	private void setExecutableIfRegularFile(Path path) throws IOException {
		if (Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
			setExecutable(path);
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

	private void log(String message) {
		System.out.println(message);
	}

	private String stackTrace(Throwable error) {
		StringWriter buffer = new StringWriter();
		error.printStackTrace(new PrintWriter(buffer));
		return buffer.toString();
	}
}
