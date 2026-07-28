package com.shale.desktop.notification;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Properties;

record WindowsNotificationRuntime(boolean eligible, Path library) {
	static WindowsNotificationRuntime detect() {
		if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows")) return unsupported();
		String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
		if (!arch.equals("amd64") && !arch.equals("x86_64")) return unsupported();
		String appPath = System.getProperty("jpackage.app-path", "");
		if (appPath.isBlank() || "javafx-maven-plugin".equals(System.getProperty("shale.launchMode"))) return unsupported();
		Path appDir = Path.of(appPath).toAbsolutePath().getParent().resolve("app");
		Path marker = appDir.resolve("shale-windows-toast.properties");
		Path library = appDir.resolve("native").resolve("shale_windows_toast.dll");
		Properties values = new Properties();
		try (InputStream input = Files.newInputStream(marker)) { values.load(input); }
		catch (IOException | RuntimeException ex) { return unsupported(); }
		boolean valid = WindowsNotificationIdentity.MARKER_FORMAT.equals(values.getProperty("format"))
				&& WindowsNotificationIdentity.APP_USER_MODEL_ID.equals(values.getProperty("appUserModelId"))
				&& WindowsNotificationIdentity.ARCHITECTURE.equals(values.getProperty("architecture"))
				&& WindowsNotificationIdentity.BRIDGE_VERSION.equals(values.getProperty("bridgeVersion"))
				&& Files.isRegularFile(library);
		return valid ? new WindowsNotificationRuntime(true, library) : unsupported();
	}
	static WindowsNotificationRuntime unsupported() { return new WindowsNotificationRuntime(false, null); }
}
