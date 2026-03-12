package com.shale.updater;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

public final class DownloadService {

	private final HttpClient http = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(15))
			.build();

	public Path downloadToTemp(String fileUrl, String fileName) throws IOException, InterruptedException {
		Path tempDir = Path.of(System.getProperty("java.io.tmpdir"), "ShaleUpdater");
		Files.createDirectories(tempDir);

		Path target = tempDir.resolve(fileName);

		HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create(fileUrl))
				.timeout(Duration.ofSeconds(60))
				.GET()
				.build();

		HttpResponse<InputStream> response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());

		if (response.statusCode() / 100 != 2) {
			throw new IOException("Download failed: HTTP " + response.statusCode());
		}

		try (InputStream in = response.body()) {
			Files.copy(in, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
		}

		return target;
	}
}