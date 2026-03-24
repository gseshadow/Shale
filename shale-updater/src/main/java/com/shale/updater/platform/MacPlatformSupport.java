package com.shale.updater.platform;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;

public class MacPlatformSupport implements PlatformSupport {

	@Override
	public Platform platform() {
		return Platform.MAC;
	}

	@Override
	public void stopRunningApp(Path installDir) throws Exception {
		Path appBinary = installDir.resolve("Contents").resolve("MacOS").resolve("Shale");
		log("macOS fallback external stop attempted for: " + appBinary);
		runBestEffort(List.of("pkill", "-f", appBinary.toString()));
		runBestEffort(List.of("pkill", "-x", "Shale"));
		Thread.sleep(1000L);
	}

	@Override
	public void restartApp(Path installDir) throws Exception {
		restartApp(installDir, null);
	}

	@Override
	public void restartApp(Path installDir, String expectedVersion) throws Exception {
		Path targetApp = relaunchTargetPath();
		Path contentsAppDir = targetApp.resolve("Contents").resolve("app");
		Path expectedJar = expectedDesktopJarPath(contentsAppDir, expectedVersion);
		Path expectedUpdaterJar = expectedUpdaterJarPath(contentsAppDir, expectedVersion);
		long updaterPid = ProcessHandle.current().pid();
		Path helperLogPath = helperLogPath();

		log("Scheduling macOS relaunch helper");
		log("Helper target path: " + targetApp);
		log("Helper start time: " + Instant.now());
		log("Helper expected target version: " + expectedVersion);
		log("Helper updater pid: " + updaterPid);
		log("Helper expected jar: " + expectedJar);
		log("Helper expected updater jar: " + expectedUpdaterJar);
		log("Helper log file: " + helperLogPath);

		startRelaunchHelper(targetApp, contentsAppDir, expectedJar, expectedUpdaterJar, updaterPid, helperLogPath);
		log("macOS relaunch helper started for: " + targetApp);
	}

	@Override
	public String appExecutableName() {
		return "Shale.app";
	}

	@Override
	public Path resolveStagedInstallDir(Path stagingDir) throws Exception {
		Path directBundle = stagingDir.resolve(appExecutableName());
		if (Files.isDirectory(directBundle)) {
			return directBundle;
		}

		try (Stream<Path> children = Files.list(stagingDir)) {
			return children
					.filter(Files::isDirectory)
					.filter(path -> path.getFileName() != null)
					.filter(path -> path.getFileName().toString().endsWith(".app"))
					.findFirst()
					.orElseThrow(() -> new IOException("macOS update zip did not contain " + appExecutableName()));
		}
	}

	@Override
	public boolean replacesInstallDir() {
		return true;
	}

	Path relaunchTargetPath() {
		return Path.of("/Applications").resolve(appExecutableName());
	}

	Path expectedDesktopJarPath(Path contentsAppDir, String expectedVersion) {
		if (expectedVersion == null || expectedVersion.isBlank()) {
			return contentsAppDir.resolve("shale-desktop.jar");
		}
		return contentsAppDir.resolve("shale-desktop-" + expectedVersion + ".jar");
	}

	Path expectedUpdaterJarPath(Path contentsAppDir, String expectedVersion) {
		if (expectedVersion == null || expectedVersion.isBlank()) {
			return null;
		}
		return contentsAppDir.resolve("lib").resolve("shale-updater-" + expectedVersion + ".jar");
	}

	Path helperLogPath() {
		return Path.of("/tmp").resolve("shale-relaunch-helper.log");
	}

	void startRelaunchHelper(
			Path targetApp,
			Path contentsAppDir,
			Path expectedJar,
			Path expectedUpdaterJar,
			long updaterPid,
			Path helperLogPath) throws Exception {
		Files.createDirectories(helperLogPath.getParent());
		String script = helperScript(targetApp, contentsAppDir, expectedJar, expectedUpdaterJar, updaterPid, helperLogPath);
		Path helperScriptFile = Files.createTempFile("shale-macos-relaunch-", ".sh");
		Files.writeString(
				helperScriptFile,
				script,
				StandardCharsets.UTF_8,
				StandardOpenOption.TRUNCATE_EXISTING);
		helperScriptFile.toFile().setExecutable(true, false);
		log("Helper script path created: " + helperScriptFile);
		log("Helper script exists after creation: " + Files.exists(helperScriptFile));
		log("Helper script executable: " + Files.isExecutable(helperScriptFile));
		log("Helper script content begins >>>");
		log(script);
		log("<<< Helper script content ends");

		ProcessBuilder helperLaunchBuilder = new ProcessBuilder("/bin/sh", helperScriptFile.toString())
				.redirectErrorStream(true);
		log("Helper launch command: " + helperLaunchBuilder.command());
		log("Helper launch working directory: " + Path.of("").toAbsolutePath());
		Process helperProcess = helperLaunchBuilder.start();
		log("Helper launch succeeded: true");
		log("Helper launch process pid: " + helperProcess.pid());
	}

	String helperScript(
			Path targetApp,
			Path contentsAppDir,
			Path expectedJar,
			Path expectedUpdaterJar,
			long updaterPid,
			Path helperLogPath) {
		return """
				#!/bin/sh
				LOG_FILE=%s
				TARGET_APP=%s
				CONTENTS_APP=%s
				EXPECTED_JAR=%s
				EXPECTED_UPDATER_JAR=%s
				EXPECTED_UPDATER_JAR_REQUIRED=%s
				UPDATER_PID=%s
				OPEN_BIN=%s
				OPEN_TARGET=%s
				log_line() {
				  printf '%%s [RelaunchHelper] %%s\\n' "$(date -u '+%%Y-%%m-%%dT%%H:%%M:%%SZ')" "$1" >> "$LOG_FILE"
				}
				: > "$LOG_FILE"
				log_line "helper started"
				log_line "helper arguments: $*"
				log_line "helper target path: $TARGET_APP"
				log_line "helper start time: $(date -u '+%%Y-%%m-%%dT%%H:%%M:%%SZ')"
				log_line "helper expected desktop jar: $EXPECTED_JAR"
				log_line "helper expected updater jar: $EXPECTED_UPDATER_JAR"
				log_line "helper expected updater jar required: $EXPECTED_UPDATER_JAR_REQUIRED"
				attempt=1
				ready_to_launch=false
				while [ "$attempt" -le 180 ]; do
				  updater_alive=false
				  if kill -0 "$UPDATER_PID" 2>/dev/null; then
				    updater_alive=true
				  fi
				  app_exists=false
				  if [ -d "$TARGET_APP" ]; then
				    app_exists=true
				  fi
				  contents_exists=false
				  if [ -d "$CONTENTS_APP" ]; then
				    contents_exists=true
				  fi
				  jar_exists=false
				  if [ -f "$EXPECTED_JAR" ]; then
				    jar_exists=true
				  fi

				  updater_jar_exists=false
				  if [ "$EXPECTED_UPDATER_JAR_REQUIRED" = false ]; then
				    updater_jar_exists=true
				  elif [ -f "$EXPECTED_UPDATER_JAR" ]; then
				    updater_jar_exists=true
				  fi

				  log_line "poll attempt=$attempt updater_alive=$updater_alive app_exists=$app_exists contents_app_exists=$contents_exists expected_jar_exists=$jar_exists expected_updater_jar_exists=$updater_jar_exists"
				  if [ "$updater_alive" = false ] && [ "$app_exists" = true ] && [ "$contents_exists" = true ] && [ "$jar_exists" = true ] && [ "$updater_jar_exists" = true ]; then
				    ready_to_launch=true
				    break
				  fi
				  attempt=$((attempt + 1))
				  sleep 1
				done
				if [ "$ready_to_launch" != true ]; then
				  log_line "relaunch checks did not pass before timeout; skipping relaunch"
				  exit 1
				fi
				OPEN_CMD="$OPEN_BIN -n $OPEN_TARGET"
				log_line "final relaunch command: $OPEN_CMD"
				open_output=$("$OPEN_BIN" -n "$OPEN_TARGET" 2>&1)
				open_exit=$?
				log_line "open stdout/stderr: $open_output"
				log_line "open exit code: $open_exit"
				if [ "$open_exit" -ne 0 ]; then
				  log_line "relaunch failed via open"
				  exit "$open_exit"
				fi
				log_line "helper finished"
				"""
				.formatted(
						shellQuote(helperLogPath.toString()),
						shellQuote(targetApp.toString()),
						shellQuote(contentsAppDir.toString()),
						shellQuote(expectedJar.toString()),
						shellQuote(expectedUpdaterJar == null ? "" : expectedUpdaterJar.toString()),
						expectedUpdaterJar == null ? "false" : "true",
						Long.toString(updaterPid),
						shellQuote("/usr/bin/open"),
						shellQuote(targetApp.toString()));
	}

	private String shellQuote(String value) {
		return "'" + value.replace("'", "'\"'\"'") + "'";
	}

	private void runBestEffort(List<String> command) {
		try {
			Process process = new ProcessBuilder(command)
					.inheritIO()
					.start();
			int exit = process.waitFor();
			System.out.println(String.join(" ", command) + " exit code: " + exit);
		} catch (Exception ex) {
			System.out.println("Ignoring macOS stop command failure for " + command + ": " + ex.getMessage());
		}
	}

	private void log(String message) {
		System.out.println(message);
	}
}
