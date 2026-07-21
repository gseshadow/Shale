package com.shale.updater;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import com.google.gson.Gson;

public final class UpdateService {

	private static final System.Logger LOG = System.getLogger(UpdateService.class.getName());

	private final HttpClient http = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(10))
			.build();

	private final Gson gson = new Gson();

	public UpdateManifest fetchManifest(String manifestUrl) throws IOException, InterruptedException {
		LOG.log(System.Logger.Level.DEBUG, "Updater manifest endpoint configured");
		HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create(manifestUrl))
				.timeout(Duration.ofSeconds(15))
				.GET()
				.build();

		HttpResponse<String> response;
		try {
			response = http.send(request, HttpResponse.BodyHandlers.ofString());
		} catch (IOException | InterruptedException ex) {
			LOG.log(System.Logger.Level.WARNING, "Updater manifest fetch failed", ex);
			throw ex;
		}

		if (response.statusCode() / 100 != 2) {
			LOG.log(System.Logger.Level.WARNING, "Updater manifest fetch failed: HTTP {0}", response.statusCode());
			throw new IOException("Manifest request failed: HTTP " + response.statusCode());
		}

		LOG.log(System.Logger.Level.DEBUG, "Updater manifest fetch success: HTTP {0}", response.statusCode());

		try {
			UpdateManifest manifest = gson.fromJson(sanitizeManifestBody(response.body()), UpdateManifest.class);
			LOG.log(System.Logger.Level.DEBUG, "Updater parsed remote version: {0}", manifest == null ? "<null>" : manifest.getVersion());
			return manifest;
		} catch (RuntimeException ex) {
			LOG.log(System.Logger.Level.WARNING, "Updater manifest parse failed", ex);
			throw new IOException("Failed to parse update manifest", ex);
		}
	}

	public boolean isUpdateAvailable(String currentVersion, UpdateManifest manifest) {
		return compareVersions(currentVersion, manifest) > 0;
	}

	public int compareVersions(String currentVersion, UpdateManifest manifest) {
		if (manifest == null || manifest.getVersion() == null || manifest.getVersion().isBlank()) {
			return 0;
		}
		return VersionComparator.compare(manifest.getVersion(), currentVersion);
	}

	private static String sanitizeManifestBody(String body) {
		if (body == null || body.isEmpty()) {
			return body;
		}
		if (body.charAt(0) == '\uFEFF') {
			return body.substring(1);
		}
		return body;
	}

}
