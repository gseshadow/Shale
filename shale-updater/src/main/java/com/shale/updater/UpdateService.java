package com.shale.updater;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import com.google.gson.Gson;

public final class UpdateService {

	private final HttpClient http = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(10))
			.build();

	private final Gson gson = new Gson();

	public UpdateManifest fetchManifest(String manifestUrl) throws IOException, InterruptedException {
		HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create(manifestUrl))
				.timeout(Duration.ofSeconds(15))
				.GET()
				.build();

		HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());

		if (response.statusCode() / 100 != 2) {
			throw new IOException("Manifest request failed: HTTP " + response.statusCode());
		}

		return gson.fromJson(response.body(), UpdateManifest.class);
	}

	public boolean isUpdateAvailable(String currentVersion, UpdateManifest manifest) {
		if (manifest == null || manifest.getVersion() == null || manifest.getVersion().isBlank()) {
			return false;
		}
		return VersionComparator.compare(manifest.getVersion(), currentVersion) > 0;
	}
}