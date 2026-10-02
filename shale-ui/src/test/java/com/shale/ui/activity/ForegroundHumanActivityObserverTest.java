package com.shale.ui.activity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import javafx.event.ActionEvent;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.input.TouchEvent;

final class ForegroundHumanActivityObserverTest {
	@Test
	void startsEmptyAndUsesControlledClockForQualifyingInputReplacement() {
		MutableClock clock = new MutableClock(Instant.parse("2026-09-28T10:15:30Z"));
		var observer = new ForegroundHumanActivityObserver(clock);
		assertTrue(observer.lastHumanActivityAt().isEmpty(), "startup must not invent human activity");

		observer.recordIfQualifying(KeyEvent.KEY_PRESSED, true, true);
		assertEquals(clock.instant(), observer.lastHumanActivityAt().orElseThrow(), "key press must use the injected clock");
		clock.set(Instant.parse("2026-09-28T10:16:30Z"));
		observer.recordIfQualifying(MouseEvent.MOUSE_PRESSED, true, true);
		assertEquals(clock.instant(), observer.lastHumanActivityAt().orElseThrow(), "later mouse input must replace the earlier timestamp");
	}

	@Test
	void scrollAndTouchPressQualifyWithoutRetainingTheirPayload() {
		Instant now = Instant.parse("2026-09-28T11:00:00Z");
		var observer = new ForegroundHumanActivityObserver(Clock.fixed(now, ZoneOffset.UTC));
		observer.recordIfQualifying(ScrollEvent.SCROLL, true, true);
		assertEquals(now, observer.lastHumanActivityAt().orElseThrow(), "scrolling must count as active reading/navigation");
		observer.stop();
		observer.recordIfQualifying(TouchEvent.TOUCH_PRESSED, true, true);
		assertEquals(now, observer.lastHumanActivityAt().orElseThrow(), "touch press must count without retaining touch details");
	}

	@Test
	void unfocusedHiddenAndNonHumanEventsDoNotCount() {
		var observer = new ForegroundHumanActivityObserver(Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));
		observer.recordIfQualifying(KeyEvent.KEY_PRESSED, true, false);
		observer.recordIfQualifying(MouseEvent.MOUSE_PRESSED, false, true);
		observer.recordIfQualifying(MouseEvent.MOUSE_MOVED, true, true);
		observer.recordIfQualifying(ActionEvent.ACTION, true, true);
		assertTrue(observer.lastHumanActivityAt().isEmpty(), "background, pointer movement, and programmatic actions must not count");
	}

	@Test
	void stopClearsActivityForLogoutAndUserSwitch() {
		var observer = new ForegroundHumanActivityObserver(Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));
		observer.recordIfQualifying(KeyEvent.KEY_PRESSED, true, true);
		observer.stop();
		assertTrue(observer.lastHumanActivityAt().isEmpty(), "logout must prevent the next user from inheriting activity");
		assertFalse(observer.isStarted(), "logout must leave the observer detached");
	}

	@Test
	void timestampCanBeReadSafelyFromAWorkerThread() throws Exception {
		Instant now = Instant.parse("2026-09-28T12:00:00Z");
		var observer = new ForegroundHumanActivityObserver(Clock.fixed(now, ZoneOffset.UTC));
		observer.recordIfQualifying(ScrollEvent.SCROLL, true, true);
		var executor = Executors.newSingleThreadExecutor();
		try {
			assertEquals(now, executor.submit(() -> observer.lastHumanActivityAt().orElseThrow()).get(5, TimeUnit.SECONDS),
					"a future background consumer must see the JavaFX-thread update");
		} finally {
			executor.shutdownNow();
		}
	}

	@Test
	void retainedStateHasNoInputContentOrIdentityFields() throws Exception {
		Set<String> allowed = Set.of("clock", "lastHumanActivityAt", "registrations", "windowListener", "authenticatedWindow", "started");
		for (Field field : ForegroundHumanActivityObserver.class.getDeclaredFields()) {
			assertTrue(allowed.contains(field.getName()), "observer must not gain payload or identity state: " + field.getName());
		}
		assertEquals(java.util.Optional.class, ForegroundHumanActivityObserver.class.getDeclaredMethod("lastHumanActivityAt").getReturnType(),
				"the public observation contract must expose timing only");
	}

	private static final class MutableClock extends Clock {
		private Instant current;
		private MutableClock(Instant current) { this.current = current; }
		void set(Instant value) { current = value; }
		@Override public ZoneId getZone() { return ZoneOffset.UTC; }
		@Override public Clock withZone(ZoneId zone) { return this; }
		@Override public Instant instant() { return current; }
	}
}
