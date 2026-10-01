package com.shale.core.update;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WindowsInstallationRegistrationDiagnosticTest {
	private static final UUID ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
	private static Map<String, String> record() {
		return Map.of("schemaVersion", "1", "installationId", ID.toString(), "ownerSid", "S-1-5-21-1-2-3-1001",
				"installRoot", "C:\\Users\\Alice\\AppData\\Local\\Shale", "supportRoot", "C:\\Users\\Alice\\AppData\\Local\\Shale");
	}

	@Test void invokesProductionReaderAndEmitsOnlyBoundedRegistrationFacts() {
		var reader = new WindowsInstallationRegistrationReader(() -> List.of(record()), ignored -> WindowsInstallationRegistrationReader.State.PRESENT);
		var bytes = new ByteArrayOutputStream();
		assertEquals(0, WindowsInstallationRegistrationDiagnostic.run(reader, ID, new PrintStream(bytes, true, StandardCharsets.UTF_8)));
		String report = bytes.toString(StandardCharsets.UTF_8);
		assertTrue(report.contains("classification=VALID"));
		assertTrue(report.contains("installationId=" + ID));
		assertTrue(report.contains("ownerSid=S-1-5-21-1-2-3-1001"));
		assertTrue(report.contains("installRoot=C:\\Users\\Alice\\AppData\\Local\\Shale"));
		for (String sensitive : List.of("tenant", "email", "token", "credential", "session"))
			assertFalse(report.toLowerCase().contains(sensitive));
	}

	@Test void nonValidClassificationFailsWithoutInventingAValidResult() {
		var reader = new WindowsInstallationRegistrationReader(() -> List.of(record()), ignored -> WindowsInstallationRegistrationReader.State.MISSING);
		var bytes = new ByteArrayOutputStream();
		assertEquals(1, WindowsInstallationRegistrationDiagnostic.run(reader, ID, new PrintStream(bytes)));
		assertTrue(bytes.toString(StandardCharsets.UTF_8).contains("classification=STALE"));
	}
}
