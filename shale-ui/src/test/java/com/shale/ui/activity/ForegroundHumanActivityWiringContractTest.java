package com.shale.ui.activity;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

final class ForegroundHumanActivityWiringContractTest {
	@Test
	void wiringUsesOnlyDiscreteInputFiltersAndPreservesNormalDispatch() throws Exception {
		String source = source("src/main/java/com/shale/ui/activity/ForegroundHumanActivityObserver.java");
		assertTrue(source.contains("addEventFilter(KeyEvent.KEY_PRESSED"), "key presses must be observed at the window boundary");
		assertTrue(source.contains("addEventFilter(MouseEvent.MOUSE_PRESSED"), "mouse presses must be observed at the window boundary");
		assertTrue(source.contains("addEventFilter(ScrollEvent.SCROLL"), "scroll must be observed at the window boundary");
		assertTrue(source.contains("addEventFilter(TouchEvent.TOUCH_PRESSED"), "touch presses must be observed at the window boundary");
		assertFalse(source.contains("MOUSE_MOVED"), "continuous pointer movement must not be observed");
		assertFalse(source.contains("consume()"), "activity observation must not alter normal input handling");
	}

	@Test
	void installationIsIdempotentAndDetachUsesTheSameHandlers() throws Exception {
		String source = source("src/main/java/com/shale/ui/activity/ForegroundHumanActivityObserver.java");
		assertTrue(source.contains("if (started && authenticatedWindow == window) return;"), "duplicate shell installation must be a no-op");
		assertTrue(source.contains("registrations.put(window, handler)"), "registered handler identity must be retained");
		assertTrue(source.contains("removeEventFilter(KeyEvent.KEY_PRESSED, handler)"), "detach must use the retained handler identity");
		assertTrue(source.contains("lastHumanActivityAt.set(null)"), "detach must clear the authenticated context timestamp");
	}

	@Test
	void ownedDialogsAreIncludedButUnrelatedWindowsAreExcluded() throws Exception {
		String source = source("src/main/java/com/shale/ui/activity/ForegroundHumanActivityObserver.java");
		assertTrue(source.contains("belongsToAuthenticatedContext(window)"), "window discovery must require authenticated ownership");
		assertTrue(source.contains("stage.getOwner()"), "owned Stage dialogs must follow their owner chain");
		assertTrue(source.contains("popup.getOwnerWindow()"), "owned JavaFX popups must follow their owner chain");
		assertTrue(source.contains("window.isShowing(), window.isFocused()"), "input must require a showing, focused Shale window");
	}

	@Test
	void authenticatedLifecycleInstallsAfterMainAndStopsBeforeLogoutOrShutdown() throws Exception {
		String scenes = source("src/main/java/com/shale/ui/navigation/SceneManager.java");
		String main = method(scenes, "public void showMain()");
		assertOrdered(main, "setScene(root, \"Shale\")", "startSessionOwnedWork()", "humanActivityObserver.start(stage)");
		String stop = method(scenes, "private void stopSessionOwnedWork()");
		assertTrue(stop.contains("humanActivityObserver.stop()"), "logout/login transitions must detach and reset observation");
		String shutdown = method(scenes, "public void shutdown()");
		assertTrue(shutdown.contains("humanActivityObserver.stop()"), "normal application shutdown must detach observation");
		String login = method(scenes, "private void showLoginSurface()");
		assertFalse(login.contains("humanActivityObserver.start"), "login credential input must never be observed");
	}

	private static String source(String path) throws Exception { return Files.readString(Path.of(path)); }
	private static String method(String source, String signature) {
		int start = source.indexOf(signature);
		if (start < 0) throw new AssertionError("Missing method: " + signature);
		int open = source.indexOf('{', start), depth = 0;
		for (int i = open; i < source.length(); i++) {
			char value = source.charAt(i);
			if (value == '{') depth++;
			if (value == '}' && --depth == 0) return source.substring(start, i + 1);
		}
		throw new AssertionError("Unclosed method: " + signature);
	}
	private static void assertOrdered(String source, String... fragments) {
		int previous = -1;
		for (String fragment : fragments) {
			int next = source.indexOf(fragment, previous + 1);
			assertTrue(next > previous, "Expected lifecycle step in order: " + fragment);
			previous = next;
		}
	}
}
