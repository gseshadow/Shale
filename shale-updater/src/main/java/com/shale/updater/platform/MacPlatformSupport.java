package com.shale.updater.platform;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.StandardOpenOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
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
		logRelaunchDiagnostics(installDir);
		List<String> command = relaunchCommand(installDir);
		log("Attempting macOS relaunch strategy: open-command");
		log("macOS relaunch command: " + command);
		if (!command.isEmpty() && "/usr/bin/open".equals(command.get(0))) {
			log("macOS /usr/bin/open exists: " + Files.exists(Path.of("/usr/bin/open")));
			log("macOS /usr/bin/open arguments: " + command.subList(1, command.size()));
		}
		try {
			Process process = startRelaunchCommand(command);
			log("macOS relaunch process started successfully");
			log("macOS relaunch process PID: " + describeProcessId(process));
			CompletableFuture<String> stdoutFuture = CompletableFuture.supplyAsync(() -> readProcessOutputBestEffort(process.getInputStream()));
			CompletableFuture<String> stderrFuture = CompletableFuture.supplyAsync(() -> readProcessOutputBestEffort(process.getErrorStream()));
			int exit = process.waitFor();
			String stdout = stdoutFuture.join();
			String stderr = stderrFuture.join();
			log("macOS relaunch command stdout: " + (stdout.isBlank() ? "<empty>" : stdout));
			log("macOS relaunch command stderr: " + (stderr.isBlank() ? "<empty>" : stderr));
			log("macOS relaunch command exit code: " + exit);
			Thread.sleep(1500L);
			log("macOS relaunch process alive after 1500ms: " + process.isAlive());
			if (exit != 0) {
				throw new IOException("macOS relaunch command exited with code " + exit
						+ (stdout.isBlank() ? "" : "; stdout: " + stdout)
						+ (stderr.isBlank() ? "" : "; stderr: " + stderr));
			}
			log("macOS relaunch command succeeded for: " + installDir);
			log("macOS relaunch diagnostic note: command succeeded but launch confirmation requires external process observation");
		} catch (Exception ex) {
			log("macOS relaunch failed: " + ex.getMessage());
			throw ex;
		}
	}

	@Override
	public boolean armPreReplacementRelaunch(Path installDir) {
		try {
			Path helperScript = createRelaunchHelperScript();
			Path helperLog = Files.createTempFile("shale-relaunch-helper-", ".log");
			String normalizedInstallPath = normalizeMacPath(installDir);
			List<String> helperCommand = List.of("/bin/sh", helperScript.toString(), normalizedInstallPath);

			log("macOS pre-replacement relaunch helper path: " + helperScript);
			log("macOS pre-replacement relaunch helper log: " + helperLog);
			log("macOS pre-replacement relaunch helper command: " + helperCommand);

			Process process = startDetachedRelaunchHelper(helperCommand, helperLog);
			log("macOS pre-replacement relaunch helper armed successfully");
			log("macOS pre-replacement relaunch helper PID: " + describeProcessId(process));
			return true;
		} catch (Exception ex) {
			log("macOS pre-replacement relaunch helper arm failed: " + ex.getMessage());
			return false;
		}
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

	List<String> relaunchCommand(Path installDir) {
		return List.of("/usr/bin/open", "-n", installDir.toString());
	}

	Process startRelaunchCommand(List<String> command) throws IOException {
		ProcessBuilder processBuilder = new ProcessBuilder(command);
		log("macOS relaunch ProcessBuilder directory: " + processBuilder.directory());
		return processBuilder.start();
	}

	Process startDetachedRelaunchHelper(List<String> command, Path helperLog) throws IOException {
		ProcessBuilder processBuilder = new ProcessBuilder(command)
				.redirectInput(ProcessBuilder.Redirect.from(Path.of("/dev/null").toFile()))
				.redirectOutput(ProcessBuilder.Redirect.appendTo(helperLog.toFile()))
				.redirectErrorStream(true);
		return processBuilder.start();
	}

	String readProcessOutput(InputStream inputStream) throws IOException {
		return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8).trim();
	}

	private String readProcessOutputBestEffort(InputStream inputStream) {
		try {
			return readProcessOutput(inputStream);
		} catch (IOException ex) {
			return "<failed to read stream: " + ex.getMessage() + ">";
		}
	}

	private String normalizeMacPath(Path path) {
		return path.toString().replace('\\', '/');
	}

	private Path createRelaunchHelperScript() throws IOException {
		Path helperScript = Files.createTempFile("shale-relaunch-helper-", ".sh");
		String script = "#!/bin/sh\n"
				+ "APP_PATH=\"$1\"\n"
				+ "echo \"[helper] waiting for app bundle: $APP_PATH\"\n"
				+ "ATTEMPTS=120\n"
				+ "while [ \"$ATTEMPTS\" -gt 0 ]; do\n"
				+ "  if [ -d \"$APP_PATH\" ]; then\n"
				+ "    echo \"[helper] app bundle found; launching via open -n\"\n"
				+ "    /usr/bin/open -n \"$APP_PATH\"\n"
				+ "    EXIT_CODE=$?\n"
				+ "    echo \"[helper] open exit code: $EXIT_CODE\"\n"
				+ "    exit $EXIT_CODE\n"
				+ "  fi\n"
				+ "  ATTEMPTS=$((ATTEMPTS - 1))\n"
				+ "  /bin/sleep 0.5\n"
				+ "done\n"
				+ "echo \"[helper] timed out waiting for app bundle\"\n"
				+ "exit 1\n";
		Files.writeString(helperScript, script, StandardCharsets.UTF_8, StandardOpenOption.TRUNCATE_EXISTING);
		helperScript.toFile().setExecutable(true, false);
		return helperScript;
	}

	private void logRelaunchDiagnostics(Path installDir) {
		Path absoluteInstallDir = installDir.toAbsolutePath().normalize();
		Path macOsDir = installDir.resolve("Contents").resolve("MacOS");
		Path launcher = macOsDir.resolve("Shale");
		log("macOS relaunch installDir absolute path: " + absoluteInstallDir);
		log("macOS relaunch installDir exists: " + Files.exists(installDir));
		log("macOS relaunch installDir is directory: " + Files.isDirectory(installDir));
		log("macOS relaunch Contents/MacOS exists: " + Files.exists(macOsDir));
		log("macOS relaunch Contents/MacOS/Shale exists: " + Files.exists(launcher));
	}

	private String describeProcessId(Process process) {
		try {
			return Long.toString(process.pid());
		} catch (UnsupportedOperationException ex) {
			return "<unavailable: " + ex.getMessage() + ">";
		}
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
