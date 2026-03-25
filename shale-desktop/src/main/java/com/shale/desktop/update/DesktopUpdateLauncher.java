package com.shale.desktop.update;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.util.Comparator;
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
			ProcessBuilder pb = buildLaunchCommand(platform, installDir, currentVersion, updaterLog);

			log(logFile, "Updater executable/script/path chosen: " + pb.command().get(0));
			log(logFile, "Updater launch working directory: " + pb.directory());
			log(logFile, "Command: " + pb.command());
			log(logFile, "Updater launch cwd explicitly set: " + (pb.directory() != null));
			Process process = pb.start();
			log(logFile, platform == AppPlatform.MAC
					? "macOS execution handoff success. PID available: " + process.pid()
					: "Updater launched. PID available: " + process.pid());

		} catch (Exception ex) {
			if (logFile != null) {
				try {
					log(logFile, "Launch failed: " + stackTrace(ex));
					if (AppPaths.isMac()) {
						log(logFile, "macOS execution handoff failure");
					}
				} catch (Exception ignored) {
				}
			}
			throw new RuntimeException("Failed to launch updater", ex);
		}
	}

	private static ProcessBuilder buildLaunchCommand(AppPlatform platform, Path installDir, String currentVersion, Path updaterLog)
			throws IOException {
		return switch (platform) {
			case WINDOWS -> buildWindowsLaunchCommand(installDir, currentVersion, updaterLog);
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

	static ProcessBuilder buildMacLaunchCommand(Path installDir, String currentVersion, Path updaterLog) throws IOException {
		Path javaBinary = resolveMacJavaBinary(installDir);
		Path updaterJar = resolveMacUpdaterJar(installDir);

		if (!Files.exists(javaBinary)) {
			throw new IllegalStateException("Bundled Java runtime not found: " + javaBinary);
		}
		if (!Files.exists(updaterJar)) {
			throw new IllegalStateException("Updater jar not found: " + updaterJar);
		}

		ProcessBuilder pb = new ProcessBuilder(
				javaBinary.toString(),
				"-jar",
				updaterJar.toString(),
				"--currentVersion", currentVersion,
				"--installDir", installDir.toString());
		pb.directory(Path.of("/").toFile());
		pb.redirectErrorStream(true);
		pb.redirectOutput(updaterLog.toFile());
		return pb;
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

	private static String stackTrace(Throwable error) {
		StringWriter buffer = new StringWriter();
		error.printStackTrace(new PrintWriter(buffer));
		return buffer.toString();
	}
}
