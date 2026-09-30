package com.shale.core.update;

import java.nio.file.Path;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/** Validated, privacy-minimal description of one installer-owned per-user Windows installation. */
public record WindowsInstallationRegistration(int schemaVersion, UUID installationId, String ownerSid,
		Path installRoot, Path supportRoot) {
	private static final Pattern SID = Pattern.compile("S-1-(?:\\d+-){1,14}\\d+");
	private static final Pattern DRIVE_ABSOLUTE = Pattern.compile("^[A-Za-z]:[\\\\/].+");

	public WindowsInstallationRegistration {
		if (schemaVersion != 1) throw new IllegalArgumentException("Unsupported installation registration schema");
		Objects.requireNonNull(installationId, "installationId");
		if (ownerSid == null || !SID.matcher(ownerSid).matches()) throw new IllegalArgumentException("Invalid Windows owner SID");
		installRoot = validatedWindowsPath(installRoot, "installRoot");
		supportRoot = validatedWindowsPath(supportRoot, "supportRoot");
		if (!fileNameEquals(installRoot, "Shale") || !fileNameEquals(supportRoot, "Shale"))
			throw new IllegalArgumentException("Registration paths must identify the expected Shale layout");
	}

	private static Path validatedWindowsPath(Path value, String field) {
		Objects.requireNonNull(value, field);
		String raw = value.toString();
		if (!DRIVE_ABSOLUTE.matcher(raw).matches() || raw.indexOf('\0') >= 0)
			throw new IllegalArgumentException(field + " must be an absolute Windows path");
		for (String part : raw.replace('/', '\\').split("\\\\"))
			if (part.equals(".") || part.equals("..")) throw new IllegalArgumentException(field + " contains traversal");
		return value.normalize();
	}

	private static boolean fileNameEquals(Path path, String expected) {
		String raw = path.toString().replace('/', '\\');
		int separator = raw.lastIndexOf('\\');
		return raw.substring(separator + 1).equalsIgnoreCase(expected);
	}
}
