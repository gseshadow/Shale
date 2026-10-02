package com.shale.ui.whatsnew;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class WhatsNewWiringContractTest {
	@Test void authenticatedShellTriggersAfterInitialRouteAndLogoutResetsTheLaunchGuard() throws Exception {
		String source = Files.readString(Path.of("src/main/java/com/shale/ui/navigation/SceneManager.java"));
		int shell = source.indexOf("setScene(root, \"Shale\")");
		int route = source.indexOf("showRouteInternal(AppRoute.myShale())", shell);
		int trigger = source.indexOf("whatsNewCoordinator.start", route);
		assertTrue(shell >= 0 && route > shell && trigger > route,
				"What's New must start only after the authenticated shell and initial route are ready");
		int teardown = source.indexOf("private void stopSessionOwnedWork()");
		assertTrue(source.indexOf("whatsNewCoordinator.reset()", teardown) > teardown,
				"logout/session teardown must reset the in-memory user-context guard");
		assertTrue(source.contains("new ApplicationReleaseReadServiceAdapter(new ApplicationReleaseReadDao(dbSessionProvider))"));
		assertTrue(source.contains("new UserReleaseStateServiceAdapter(new UserReleaseStateDao(dbSessionProvider))"));
	}

	@Test void bothPrimaryActionAndOrdinaryWindowCloseCrossTheDismissalBoundary() throws Exception {
		String source = Files.readString(Path.of("src/main/java/com/shale/ui/whatsnew/WhatsNewDialog.java"));
		assertTrue(source.contains("stage.setOnCloseRequest"), "the ordinary window close must count as dismissal");
		assertTrue(source.contains("ActionButtonFactory.semantic(\"Got it\""), "the primary action must be explicit");
		assertTrue(source.contains("completed.compareAndSet(false, true)"), "either close path must acknowledge at most once");
	}
}
