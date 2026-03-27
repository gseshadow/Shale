package com.shale.desktop.update;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.stream.Stream;

import com.shale.core.platform.AppPaths;
import com.shale.core.platform.AppPlatform;

public final class DesktopUpdateLauncher {

	private static final String APP_NAME = "Shale";

	private DesktopUpdateLauncher() {
	}

	public static void launchUpdater(String currentVersion) {
		Path logFile = AppPaths.appLogFile(APP_NAME, "update-launcher.log");

		try {
			Files.createDirectories(logFile.getParent());
			Path installDir = DesktopInstallLocator.detectInstallDir();
			AppPlatform platform = AppPaths.platform();

			log(logFile, "==== Launch attempt " + LocalDateTime.now() + " ====");
			log(logFile, "LaunchUpdater entry");
			log(logFile, "Selected platform: " + platform);
			log(logFile, "Current version: " + currentVersion);
			log(logFile, "Detected install dir: " + installDir);

			Path updaterLog = AppPaths.appLogFile(APP_NAME, "updater-output.log");
			Files.createDirectories(updaterLog.getParent());
			LaunchPlan launchPlan = buildLaunchCommand(platform, installDir, currentVersion, updaterLog);
			ProcessBuilder pb = launchPlan.processBuilder();

			if (launchPlan.macHelperScript() != null) {
				log(logFile, "macOS helper script path: " + launchPlan.macHelperScript());
				log(logFile, "macOS helper updater command: " + launchPlan.macUpdaterCommand());
				log(logFile, "macOS helper working directory: " + launchPlan.helperWorkingDirectory());
			}
			log(logFile, "Updater executable/script/path chosen: " + pb.command().get(0));
			log(logFile, "Updater launch working directory: " + pb.directory());
			log(logFile, "Command: " + pb.command());
			log(logFile, "Updater launch cwd explicitly set: " + (pb.directory() != null));
			Process process = pb.start();
			log(logFile, platform == AppPlatform.MAC
					? "macOS detached helper launch success. PID available: " + process.pid()
					: "Updater launched. PID available: " + process.pid());

		} catch (Exception ex) {
			if (logFile != null) {
				try {
					log(logFile, "Launch failed: " + stackTrace(ex));
					if (AppPaths.isMac()) {
						log(logFile, "macOS detached helper launch failure");
					}
				} catch (Exception ignored) {
				}
			}
			throw new RuntimeException("Failed to launch updater", ex);
		}
	}

	private static LaunchPlan buildLaunchCommand(AppPlatform platform, Path installDir, String currentVersion, Path updaterLog)
			throws IOException {
		return switch (platform) {
			case WINDOWS -> new LaunchPlan(buildWindowsLaunchCommand(installDir, currentVersion, updaterLog), null, null, null);
			case MAC -> buildMacLaunchCommand(installDir, currentVersion, updaterLog);
			default -> throw new IllegalStateException("In-app updates are not available on this platform yet.");
		};
	}

	private static ProcessBuilder buildWindowsLaunchCommand(Path installDir, String currentVersion, Path updaterLog) {
		Path updaterExe = installDir.resolve("app").resolve("updater").resolve("ShaleUpdater.exe");
		if (!Files.exists(updaterExe)) {
			Path alt = installDir.resolve("updater").resolve("ShaleUpdater.exe");
			if (Files.exists(alt)) {
				updaterExe = alt;
			}
		}

		if (!Files.exists(updaterExe)) {
			throw new IllegalStateException("Updater not found: " + updaterExe);
		}

		ProcessBuilder pb = new ProcessBuilder(
				updaterExe.toString(),
				"--currentVersion", currentVersion,
				"--installDir", installDir.toString());
		pb.redirectErrorStream(true);
		pb.redirectOutput(updaterLog.toFile());
		return pb;
	}

	static LaunchPlan buildMacLaunchCommand(Path installDir, String currentVersion, Path updaterLog) throws IOException {
		Path javaBinary = resolveMacJavaBinary(installDir);
		Path updaterJar = resolveMacUpdaterJar(installDir);
		Path helperWorkingDirectory = Path.of("/");

		if (!Files.exists(javaBinary)) {
			throw new IllegalStateException("Bundled Java runtime not found: " + javaBinary);
		}
		if (!Files.exists(updaterJar)) {
			throw new IllegalStateException("Updater jar not found: " + updaterJar);
		}

		List<String> updaterArgs = List.of(
				javaBinary.toString(),
				"-jar",
				updaterJar.toString(),
				"--currentVersion", currentVersion,
				"--installDir", installDir.toString());
		String updaterCommand = shellJoin(updaterArgs);
		Path helperScript = createMacDetachedHelperScript(updaterCommand, updaterLog, helperWorkingDirectory);

		ProcessBuilder pb = new ProcessBuilder("/bin/sh", helperScript.toString());
		pb.directory(helperWorkingDirectory.toFile());
		pb.redirectErrorStream(true);
		pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
		return new LaunchPlan(pb, helperScript, updaterCommand, helperWorkingDirectory);
	}

	static Path createMacDetachedHelperScript(String updaterCommand, Path updaterLog, Path helperWorkingDirectory) throws IOException {
		Path helperScript = Files.createTempFile("shale-updater-handoff-", ".sh");
		String script = "#!/bin/sh\n"
				+ "cd " + shellQuote(helperWorkingDirectory.toString()) + "\n"
				+ "nohup " + updaterCommand + " >> " + shellQuote(updaterLog.toString())
				+ " 2>&1 < /dev/null &\n";
		Files.writeString(helperScript, script, StandardCharsets.UTF_8, StandardOpenOption.TRUNCATE_EXISTING);
		try {
			Files.setPosixFilePermissions(helperScript, EnumSet.of(
					PosixFilePermission.OWNER_READ,
					PosixFilePermission.OWNER_WRITE,
					PosixFilePermission.OWNER_EXECUTE,
					PosixFilePermission.GROUP_READ,
					PosixFilePermission.GROUP_EXECUTE,
					PosixFilePermission.OTHERS_READ,
					PosixFilePermission.OTHERS_EXECUTE));
		} catch (UnsupportedOperationException ex) {
			if (!helperScript.toFile().setExecutable(true, false)) {
				throw new IOException("Failed to mark helper script executable: " + helperScript, ex);
			}
		}
		return helperScript;
	}

	static Path resolveMacUpdaterJar(Path installDir) throws IOException {
		for (Path libDir : macLibDirs(installDir)) {
			if (!Files.isDirectory(libDir)) {
				continue;
			}

			try (Stream<Path> stream = Files.list(libDir)) {
				return stream
						.filter(Files::isRegularFile)
						.filter(path -> path.getFileName() != null)
						.filter(path -> path.getFileName().toString().startsWith("shale-updater-"))
						.filter(path -> path.getFileName().toString().endsWith(".jar"))
						.sorted(Comparator.comparing(path -> path.getFileName().toString()))
						.findFirst()
						.orElse(null);
			}
		}

		return installDir.resolve("Contents").resolve("app").resolve("lib").resolve("shale-updater.jar");
	}

	static Path resolveMacJavaBinary(Path installDir) {
		for (Path candidate : List.of(
				installDir.resolve("Contents").resolve("runtime").resolve("Contents").resolve("Home").resolve("bin").resolve("java"),
				installDir.resolve("Contents").resolve("runtime").resolve("bin").resolve("java"),
				installDir.resolve("runtime").resolve("Contents").resolve("Home").resolve("bin").resolve("java"),
				Path.of(System.getProperty("java.home")).resolve("bin").resolve("java"))) {
			if (Files.exists(candidate)) {
				return candidate;
			}
		}

		return installDir.resolve("Contents").resolve("runtime").resolve("Contents").resolve("Home").resolve("bin").resolve("java");
	}

	private static List<Path> macLibDirs(Path installDir) {
		return List.of(
				installDir.resolve("Contents").resolve("app").resolve("lib"),
				installDir.resolve("app").resolve("lib"),
				installDir.resolve("lib"));
	}

	private static void log(Path logFile, String line) throws IOException {
		System.out.println("[Updater] " + line);
		Files.writeString(
				logFile,
				line + System.lineSeparator(),
				StandardCharsets.UTF_8,
				StandardOpenOption.CREATE,
				StandardOpenOption.APPEND);
	}

	private static String shellJoin(List<String> args) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < args.size(); i++) {
			if (i > 0) {
				sb.append(' ');
			}
			sb.append(shellQuote(args.get(i)));
		}
		return sb.toString();
	}

	private static String shellQuote(String value) {
		return "'" + value.replace("'", "'\\''") + "'";
	}

	private static String stackTrace(Throwable error) {
		StringWriter buffer = new StringWriter();
		error.printStackTrace(new PrintWriter(buffer));
		return buffer.toString();
	}

	record LaunchPlan(ProcessBuilder processBuilder, Path macHelperScript, String macUpdaterCommand, Path helperWorkingDirectory) {
	}
}
