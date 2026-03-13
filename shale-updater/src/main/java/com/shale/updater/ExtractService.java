package com.shale.updater;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class ExtractService {

	public Path extractToStaging(Path zipFile, String version) throws IOException {
		Path tempDir = Path.of(System.getProperty("java.io.tmpdir"), "ShaleUpdater");
		Files.createDirectories(tempDir);

		Path stagingDir = tempDir.resolve("staging-" + version);

		deleteRecursively(stagingDir);
		Files.createDirectories(stagingDir);

		try (InputStream fis = Files.newInputStream(zipFile);
			 ZipInputStream zis = new ZipInputStream(fis)) {

			ZipEntry entry;

			while ((entry = zis.getNextEntry()) != null) {
				Path target = stagingDir.resolve(entry.getName()).normalize();

				if (!target.startsWith(stagingDir)) {
					throw new IOException("Unsafe zip entry: " + entry.getName());
				}

				if (entry.isDirectory()) {
					ensureDirectoryTree(stagingDir, target);
				} else {
					Path parent = target.getParent();
					if (parent != null) {
						ensureDirectoryTree(stagingDir, parent);
					}

					if (Files.exists(target) && Files.isDirectory(target)) {
						deleteRecursively(target);
					}

					Files.copy(zis, target, StandardCopyOption.REPLACE_EXISTING);
				}

				zis.closeEntry();
			}
		}

		return stagingDir;
	}

	private void ensureDirectoryTree(Path root, Path dir) throws IOException {
		if (dir == null) {
			return;
		}
		if (!dir.startsWith(root)) {
			throw new IOException("Directory escapes staging root: " + dir);
		}

		Path relative = root.relativize(dir);
		Path current = root;

		for (int i = 0; i < relative.getNameCount(); i++) {
			current = current.resolve(relative.getName(i));

			if (Files.exists(current)) {
				if (Files.isDirectory(current)) {
					continue;
				}
				Files.delete(current);
			}

			Files.createDirectory(current);
		}
	}

	private void deleteRecursively(Path path) throws IOException {
		if (path == null || !Files.exists(path)) {
			return;
		}

		try (var stream = Files.walk(path)) {
			stream.sorted(Comparator.reverseOrder()).forEach(p -> {
				try {
					Files.deleteIfExists(p);
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
			});
		} catch (RuntimeException e) {
			if (e.getCause() instanceof IOException io) {
				throw io;
			}
			throw e;
		}
	}
}