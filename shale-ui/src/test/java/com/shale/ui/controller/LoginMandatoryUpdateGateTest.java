package com.shale.ui.controller;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import com.shale.ui.services.UiUpdateLauncher;

class LoginMandatoryUpdateGateTest {
	@Test
	void onlyAnAvailableMandatoryManifestBlocksStartup() {
		assertTrue(LoginController.requiresMandatoryUpdate(new UiUpdateLauncher.UpdateCheckResult(true, true)),
				"an available mandatory release must gate entry to the application shell");
		assertFalse(LoginController.requiresMandatoryUpdate(new UiUpdateLauncher.UpdateCheckResult(true, false)),
				"an optional update must remain dismissible and must not gate startup");
		assertFalse(LoginController.requiresMandatoryUpdate(new UiUpdateLauncher.UpdateCheckResult(false, true)),
				"a mandatory flag without an applicable newer release must not block startup");
		assertFalse(LoginController.requiresMandatoryUpdate(null),
				"an absent compatibility result remains governed by the existing policy path");
	}

	@Test
	void mandatoryPromptReturnsBeforeTheShellAndDeclineExits() throws Exception {
		String source = Files.readString(Path.of("src/main/java/com/shale/ui/controller/LoginController.java"))
				.replace("\r\n", "\n");
		int method = source.indexOf("private void handlePostLoginFlow");
		int mandatory = source.indexOf("presentAvailableUpdate(true, Platform::exit)", method);
		int gateReturn = source.indexOf("return;", mandatory);
		int shell = source.indexOf("sceneManager.showMain()", method);
		assertTrue(mandatory > method && gateReturn > mandatory && shell > gateReturn,
				"mandatory startup must offer Update/Exit and return before shell construction");
	}
}
