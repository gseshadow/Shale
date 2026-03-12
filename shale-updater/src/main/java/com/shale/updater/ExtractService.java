package com.shale.updater;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class ExtractService {

	public Path extractToStaging(Path zipFile, String version) throws IOException {
		Path stagingDir = Path.of(System.getProperty("java.io.tmpdir"), "ShaleUpdater", "staging-" + version);

		if (Files.exists(stagingDir)) {
			deleteRecursively(stagingDir);
		}
		Files.createDirectories(stagingDir);

		try (InputStream in = Files.newInputStream(zipFile);
			 ZipInputStream zipIn = new ZipInputStream(in)) {

			ZipEntry entry;
			while ((entry = zipIn.getNextEntry()) != null) {
				Path target = stagingDir.resolve(entry.getName()).normalize();

				if (!target.startsWith(stagingDir)) {
					throw new IOException("Blocked zip slip entry: " + entry.getName());
				}

				if (entry.isDirectory()) {
					Files.createDirectories(target);
				} else {
					Path parent = target.getParent();
					if (parent != null) {
						Files.createDirectories(parent);
					}
					Files.copy(zipIn, target, StandardCopyOption.REPLACE_EXISTING);
				}

				zipIn.closeEntry();
			}
		}

		return stagingDir;
	}

	private void deleteRecursively(Path path) throws IOException {
		if (!Files.exists(path)) {
			return;
		}

		try (var stream = Files.walk(path)) {
			stream.sorted((a, b) -> b.getNameCount() - a.getNameCount())
					.forEach(p -> {
						try {
							Files.deleteIfExists(p);
						} catch (IOException e) {
							throw new RuntimeException(e);
						}
					});
		}
	}
}