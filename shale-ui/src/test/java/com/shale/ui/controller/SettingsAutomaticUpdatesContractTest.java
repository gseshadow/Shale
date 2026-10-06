package com.shale.ui.controller;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
		assertTrue(fxml.indexOf("fx:id=\"automaticUpdatesRow\"") < fxml.indexOf("fx:id=\"caseConfigurationGroup\""),
				"Automatic Updates is a personal application preference, not an Administration capability");
	}

	@Test void automaticUpdatePreferenceIsNotBoundToAdministratorAuthorization() throws Exception {
		String source = Files.readString(Path.of("src/main/java/com/shale/ui/controller/SettingsController.java"));
		String load = methodBody(source, "loadAutomaticUpdates");
		String change = methodBody(source, "onAutomaticUpdatesChanged");
		assertFalse(load.contains("isAdminUser"), "An ordinary authenticated user must receive an enabled preference control");
		assertTrue(load.contains("automaticUpdatesCheck.setDisable(false)"),
				"Both ordinary users and administrators must be able to interact with the supported control");
		assertFalse(change.contains("isAdminUser"), "Saving the application preference must not pass administrator authority");
		assertTrue(change.contains("capability.get().change(requested)"),
				"The selected value must use the existing runtime preference capability");
	}

	@Test void unsupportedRuntimeStillDisablesOnlyAutomaticUpdatesAndAdminControlsStayProtected() throws Exception {
		String source = Files.readString(Path.of("src/main/java/com/shale/ui/controller/SettingsController.java"));
		String load = methodBody(source, "loadAutomaticUpdates");
		String visibility = methodBody(source, "updateAdminControlsVisibility");
		assertTrue(load.contains("if (capability.isEmpty())"));
		assertTrue(load.contains("automaticUpdatesCheck.setDisable(true)"),
				"A runtime without preference storage must keep the control disabled");
		assertTrue(visibility.contains("setVisibleManaged(userManagementRow, hasAdminContext() && userDao != null)"));
		assertTrue(visibility.contains("setVisibleManaged(adminSessionsRow, hasAdminContext() && runtimeBridge != null)"));
		assertTrue(visibility.contains("setVisibleManaged(auditLogRow, admin)"),
				"Removing Auto Update authorization must not expose administrator-only Settings controls");
	}

	private static String methodBody(String source, String methodName) {
		int signature = source.indexOf(methodName + "(");
		assertTrue(signature >= 0, "Expected SettingsController method " + methodName);
		int open = source.indexOf('{', signature);
		int depth = 0;
		for (int index = open; index < source.length(); index++) {
			if (source.charAt(index) == '{') depth++;
			else if (source.charAt(index) == '}' && --depth == 0) return source.substring(open, index + 1);
		}
		return fail("Expected complete method body for " + methodName);
	}
}
