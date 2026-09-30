package com.shale.ui.controller;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

final class SettingsAutomaticUpdatesContractTest {
	@Test void settingsCopyIsMachineScopedAndQualifiesInSessionScheduling() throws Exception {
		String fxml;
		try (var input = getClass().getResourceAsStream("/fxml/settings.fxml")) {
			assertNotNull(input); fxml = new String(input.readAllBytes(), StandardCharsets.UTF_8);
		}
		assertTrue(fxml.contains("Allow eligible updates while Shale is running and idle"));
		assertTrue(fxml.contains("This setting applies to this workstation."));
		assertTrue(fxml.contains("Closed, logged-out, and sleeping computers are not woken."));
		assertTrue(fxml.contains("2:00–4:00 AM local window"));
	}
}
