package com.shale.desktop.update;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

final class DesktopUiUpdateLauncherTest {

	private static final String OS_NAME = "os.name";
	private static final String APP_VERSION = "APP_VERSION";

	private String originalOsName;
	private String originalAppVersion;

	@AfterEach
	void restoreSystemProperties() {
		restoreProperty(OS_NAME, originalOsName);
		restoreProperty(APP_VERSION, originalAppVersion);
	}

	@Test
	void checkForUpdateOnMacStillRunsDetectionAndCanReturnUpdateAvailable() throws Exception {
		rememberOriginalProperties();
		System.setProperty(OS_NAME, "Mac OS X");
		System.setProperty(APP_VERSION, "1.0.14");

		AtomicInteger manifestRequests = new AtomicInteger();
		try (ManifestServer server = new ManifestServer("""
				{
				  "version": "1.0.16",
				  "mandatory": false,
				  "macZipUrl": "https://example.test/ShaleApp-1.0.16-mac.zip",
				  "macSha256": "abc123"
				}
				""", manifestRequests)) {
			var launcher = new DesktopUiUpdateLauncher(new com.shale.updater.UpdateService(), server.url());

			var result = launcher.checkForUpdate();

			assertEquals(1, manifestRequests.get(), "macOS detection should still fetch the manifest");
			assertTrue(result.updateAvailable(), "a newer mac manifest version with a mac asset should produce updateAvailable=true");
		}
	}

	@Test
	void launchUpdaterOnMacDelegatesToUpdaterExecutionFlow() {
		rememberOriginalProperties();
		System.setProperty(OS_NAME, "Mac OS X");
		System.setProperty(APP_VERSION, "1.0.14");

		AtomicInteger launchCalls = new AtomicInteger();
		AtomicReference<String> launchedVersion = new AtomicReference<>();
		var launcher = new DesktopUiUpdateLauncher(
				new com.shale.updater.UpdateService(),
				"https://example.test/manifest.json",
				currentVersion -> {
					launchCalls.incrementAndGet();
					launchedVersion.set(currentVersion);
				});

		launcher.launchUpdater();

		assertEquals(1, launchCalls.get(), "macOS launch should hand off into the updater execution flow");
		assertEquals("1.0.14", launchedVersion.get(), "launcher should pass the current app version to the updater");
	}

	private void rememberOriginalProperties() {
		if (originalOsName == null) {
			originalOsName = System.getProperty(OS_NAME);
		}
		if (originalAppVersion == null) {
			originalAppVersion = System.getProperty(APP_VERSION);
		}
	}

	private static void restoreProperty(String key, String value) {
		if (value == null) {
			System.clearProperty(key);
			return;
		}
		System.setProperty(key, value);
	}

	private static final class ManifestServer implements AutoCloseable {
		private final HttpServer server;
		private final AtomicInteger requestCount;
		private final String responseBody;

		private ManifestServer(String responseBody, AtomicInteger requestCount) throws IOException {
			this.responseBody = responseBody;
			this.requestCount = requestCount;
			this.server = HttpServer.create(new InetSocketAddress(0), 0);
			this.server.createContext("/manifest.json", this::handleManifest);
			this.server.start();
		}

		private void handleManifest(HttpExchange exchange) throws IOException {
			requestCount.incrementAndGet();
			byte[] body = responseBody.getBytes(StandardCharsets.UTF_8);
			exchange.getResponseHeaders().add("Content-Type", "application/json");
			exchange.sendResponseHeaders(200, body.length);
			try (OutputStream out = exchange.getResponseBody()) {
				out.write(body);
			}
		}

		private String url() {
			return "http://127.0.0.1:" + server.getAddress().getPort() + "/manifest.json";
		}

		@Override
		public void close() {
			server.stop(0);
		}
	}
}
