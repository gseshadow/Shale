package com.shale.ui.controller;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

final class SettingsAutomaticUpdatesContractTest {
	@Test void settingsCopyIsMachineScopedAndDoesNotPromiseScheduling() throws Exception {
		String fxml;
		try (var input = getClass().getResourceAsStream("/fxml/settings.fxml")) {
			assertNotNull(input); fxml = new String(input.readAllBytes(), StandardCharsets.UTF_8);
		}
		assertTrue(fxml.contains("Allow unattended automatic updates on this workstation"));
		assertTrue(fxml.contains("This setting applies to this workstation."));
		assertTrue(fxml.contains("Scheduling will be handled separately."));
		assertFalse(fxml.contains("2:00 AM"));
	}
}
