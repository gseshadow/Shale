package com.shale.updater.platform;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

public class MacPlatformSupport implements PlatformSupport {

	private final AtomicBoolean relaunchHelperArmed = new AtomicBoolean(false);

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
		if (!relaunchHelperArmed.get()) {
			throw new IOException("macOS relaunch helper was not armed before replacement");
		}
		log("macOS relaunch delegated to pre-armed detached helper");
	}

	@Override
	public void armRelaunchHelper(Path installDir, String expectedVersion) throws Exception {
		if (expectedVersion == null || expectedVersion.isBlank()) {
			log("Skipping macOS relaunch helper arming because expected version is blank");
			return;
		}

		Path targetApp = relaunchTargetPath();
		Path markerJar = targetApp.resolve("Contents/app/shale-desktop-" + expectedVersion + ".jar");
		Path updaterJar = targetApp.resolve("Contents/app/lib/shale-updater-" + expectedVersion + ".jar");
		Path appBinary = targetApp.resolve("Contents/MacOS/Shale");
		long updaterPid = ProcessHandle.current().pid();
		Path helperLog = helperLogPath();

		String helperScript = helperScript(updaterPid, markerJar, updaterJar, appBinary, helperLog);
		ProcessBuilder builder = new ProcessBuilder("nohup", "/bin/sh", "-c", helperScript).redirectErrorStream(true);

		log("Arming detached macOS relaunch helper before backup/replace");
		log("macOS relaunch helper updater pid: " + updaterPid);
		log("macOS relaunch helper marker jar: " + markerJar);
		log("macOS relaunch helper updater jar: " + updaterJar);
		log("macOS relaunch helper app binary: " + appBinary);
		log("macOS relaunch helper log path: " + helperLog);
		log("macOS relaunch helper command: " + builder.command());

		Process process = builder.start();
		log("macOS relaunch helper launcher process started with pid: " + process.pid());
		relaunchHelperArmed.set(true);
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

	Path helperLogPath() {
		return Path.of("/tmp/shale-updater-relaunch-helper.log");
	}

	String helperScript(long updaterPid, Path markerJar, Path updaterJar, Path appBinary, Path helperLog) {
		String updaterPidValue = Long.toString(updaterPid);
		String markerPath = shellQuote(markerJar.toString());
		String updaterPath = shellQuote(updaterJar.toString());
		String appBinaryPath = shellQuote(appBinary.toString());
		String helperLogPath = shellQuote(helperLog.toString());

		return "LOG_FILE=" + helperLogPath + ";"
				+ "log(){ printf '%s %s\\n' \"$(date '+%Y-%m-%d %H:%M:%S')\" \"$1\" >> \"$LOG_FILE\"; };"
				+ "log \"helper booting (updater pid " + updaterPidValue + ")\";"
				+ "while kill -0 " + updaterPidValue + " 2>/dev/null; do sleep 1; done;"
				+ "log \"updater process exited\";"
				+ "until [ -f " + markerPath + " ] && [ -f " + updaterPath + " ]; do "
				+ "log \"waiting for marker/updater jars\"; sleep 1; "
				+ "done;"
				+ "log \"marker and updater jars ready\";"
				+ "if [ ! -x " + appBinaryPath + " ]; then log \"app binary missing/not executable: " + appBinaryPath + "\"; exit 1; fi;"
				+ appBinaryPath + " >> \"$LOG_FILE\" 2>&1 &"
				+ "log \"relaunch command executed\";";
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
