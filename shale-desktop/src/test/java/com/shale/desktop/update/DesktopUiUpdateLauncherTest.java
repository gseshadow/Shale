package com.shale.desktop.update;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.nio.file.Path;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.shale.core.update.UpdateAttemptState;
import com.shale.core.update.UpdateAttemptStore;
import com.shale.core.update.UpdateExecutionLock;
import com.shale.core.update.UpdateInvocationMode;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

final class DesktopUiUpdateLauncherTest {

	private static final String OS_NAME = "os.name";
	private static final String APP_VERSION = "APP_VERSION";

	private String originalOsName;
	private String originalAppVersion;
	private String originalAttemptDir;
	private String originalExecutionLock;

	@AfterEach
	void restoreSystemProperties() {
		restoreProperty(OS_NAME, originalOsName);
		restoreProperty(APP_VERSION, originalAppVersion);
		restoreProperty("SHALE_UPDATE_ATTEMPT_DIR", originalAttemptDir);
		restoreProperty("SHALE_UPDATE_EXECUTION_LOCK", originalExecutionLock);
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
	void launchUpdaterOnMacDelegatesToUpdaterExecutionFlow(@TempDir Path tempDir) throws Exception {
		rememberOriginalProperties();
		System.setProperty(OS_NAME, "Mac OS X");
		System.setProperty(APP_VERSION, "1.0.14");
		System.setProperty("SHALE_UPDATE_ATTEMPT_DIR", tempDir.toString());
		System.setProperty("SHALE_UPDATE_EXECUTION_LOCK", tempDir.resolve("update-execution.lock").toString());

		AtomicInteger launchCalls = new AtomicInteger();
		AtomicInteger shutdownCalls = new AtomicInteger();
		AtomicReference<String> launchedVersion = new AtomicReference<>();
		AtomicReference<UUID> attemptId = new AtomicReference<>();
		var launcher = new DesktopUiUpdateLauncher(
				new com.shale.updater.UpdateService(),
				"https://example.test/manifest.json",
				(currentVersion, id, attemptDirectory) -> {
					launchCalls.incrementAndGet();
					launchedVersion.set(currentVersion);
					attemptId.set(id);
					assertEquals(tempDir, attemptDirectory);
				},
				shutdownCalls::incrementAndGet);

		launcher.launchUpdater();

		assertEquals(1, launchCalls.get(), "macOS launch should hand off into the updater execution flow");
		assertEquals("1.0.14", launchedVersion.get(), "launcher should pass the current app version to the updater");
		assertEquals(1, shutdownCalls.get(), "macOS launch should trigger app self-shutdown after updater handoff succeeds");
		var attempt = new UpdateAttemptStore(tempDir).read(attemptId.get()).orElseThrow();
		assertEquals("1.0.14", attempt.fromVersion(), "attempt should capture the actual source version");
		assertEquals("PRODUCTION", attempt.releaseChannel(), "attempt should capture the stable release channel");
		assertEquals(UpdateAttemptState.UPDATER_LAUNCHED, attempt.state(), "process start is launch evidence, not completion");
	}

	@Test
	void launchUpdaterOnWindowsDoesNotTriggerAppShutdown(@TempDir Path tempDir) {
		rememberOriginalProperties();
		System.setProperty(OS_NAME, "Windows 11");
		System.setProperty(APP_VERSION, "1.0.14");
		System.setProperty("SHALE_UPDATE_ATTEMPT_DIR", tempDir.toString());
		System.setProperty("SHALE_UPDATE_EXECUTION_LOCK", tempDir.resolve("update-execution.lock").toString());

		AtomicInteger launchCalls = new AtomicInteger();
		AtomicInteger shutdownCalls = new AtomicInteger();
		var launcher = new DesktopUiUpdateLauncher(
				new com.shale.updater.UpdateService(),
				"https://example.test/manifest.json",
				(currentVersion, attemptId, attemptDirectory) -> launchCalls.incrementAndGet(),
				shutdownCalls::incrementAndGet);

		launcher.launchUpdater();

		assertEquals(1, launchCalls.get(), "Windows launch should still hand off into the updater execution flow");
		assertEquals(0, shutdownCalls.get(), "Windows launch should leave shutdown control to the updater");
	}

	@Test void occupiedExecutionLockPreventsProcessAndAttemptCreation(@TempDir Path tempDir) throws Exception {
		rememberOriginalProperties();
		System.setProperty("SHALE_UPDATE_ATTEMPT_DIR", tempDir.resolve("attempts").toString());
		Path lockPath = tempDir.resolve("update-execution.lock");
		System.setProperty("SHALE_UPDATE_EXECUTION_LOCK", lockPath.toString());
		AtomicInteger launches = new AtomicInteger();
		var launcher = new DesktopUiUpdateLauncher(new com.shale.updater.UpdateService(), "ignored",
				(version, id, directory) -> launches.incrementAndGet(), () -> {});
		try (var lock = UpdateExecutionLock.tryAcquire(lockPath).orElseThrow()) {
			var failure = org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, launcher::launchUpdater);
			assertEquals("A Shale update is already in progress.", failure.getMessage());
		}
		assertEquals(0, launches.get(), "collision must not create a duplicate updater process");
		assertTrue(!java.nio.file.Files.exists(tempDir.resolve("attempts")), "collision occurs before Phase 12 begins");
	}

	@Test void unattendedHandoffCreatesOneAttemptAndCannotUseManualLauncher(@TempDir Path tempDir) {
		rememberOriginalProperties();
		System.setProperty(OS_NAME, "Windows 11"); System.setProperty(APP_VERSION, "1.0.129");
		System.setProperty("SHALE_UPDATE_ATTEMPT_DIR", tempDir.resolve("attempts").toString());
		System.setProperty("SHALE_UPDATE_EXECUTION_LOCK", tempDir.resolve("update-execution.lock").toString());
		AtomicInteger manual = new AtomicInteger(); AtomicInteger unattended = new AtomicInteger();
		var launcher = new DesktopUiUpdateLauncher(new com.shale.updater.UpdateService(), "ignored",
				(version, id, directory) -> manual.incrementAndGet(),
				(version, id, directory, mode) -> {
					assertEquals(UpdateInvocationMode.UNATTENDED, mode); unattended.incrementAndGet();
				}, () -> {});
		launcher.launchUpdater(UpdateInvocationMode.UNATTENDED);
		assertEquals(0, manual.get(), "automatic scheduling must not enter the legacy manual launch path");
		assertEquals(1, unattended.get(), "one eligible handoff creates exactly one updater process request");
		try (var files = java.nio.file.Files.list(tempDir.resolve("attempts"))) {
			assertEquals(1, files.filter(path -> path.getFileName().toString().endsWith(".properties")).count(),
					"one handoff creates exactly one Phase 12 attempt");
		} catch (IOException failure) { throw new AssertionError(failure); }
	}

	private void rememberOriginalProperties() {
		if (originalOsName == null) {
			originalOsName = System.getProperty(OS_NAME);
		}
		if (originalAppVersion == null) {
			originalAppVersion = System.getProperty(APP_VERSION);
		}
		if (originalAttemptDir == null) originalAttemptDir = System.getProperty("SHALE_UPDATE_ATTEMPT_DIR");
		if (originalExecutionLock == null) originalExecutionLock = System.getProperty("SHALE_UPDATE_EXECUTION_LOCK");
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
