package com.shale.ui.activity;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import javafx.collections.ListChangeListener;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.event.EventType;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.input.TouchEvent;
import javafx.stage.PopupWindow;
import javafx.stage.Stage;
import javafx.stage.Window;

/**
 * Process-local observation of the latest foreground human input in the authenticated Shale shell.
 * Only a timestamp is retained; event payloads and user, control, key, pointer, and content data are not.
 */
public final class ForegroundHumanActivityObserver implements AutoCloseable {
	private final Clock clock;
	private final AtomicReference<Instant> lastHumanActivityAt = new AtomicReference<>();
	private final Map<Window, EventHandler<Event>> registrations = new IdentityHashMap<>();
	private final ListChangeListener<Window> windowListener = change -> refreshOwnedWindows();
	private Window authenticatedWindow;
	private boolean started;

	public ForegroundHumanActivityObserver(Clock clock) {
		this.clock = Objects.requireNonNull(clock, "clock");
	}

	/** Installs exactly one registration for an authenticated shell and its owned windows. */
	public void start(Window window) {
		Objects.requireNonNull(window, "window");
		if (started && authenticatedWindow == window) return;
		stop();
		authenticatedWindow = window;
		started = true;
		try {
			Window.getWindows().addListener(windowListener);
			refreshOwnedWindows();
		} catch (RuntimeException installationFailure) {
			stop();
			throw installationFailure;
		}
	}

	/** Detaches all filters and clears activity so a later authenticated user cannot inherit it. */
	public void stop() {
		if (started) Window.getWindows().removeListener(windowListener);
		for (var registration : new ArrayList<>(registrations.entrySet())) detach(registration.getKey(), registration.getValue());
		registrations.clear();
		authenticatedWindow = null;
		started = false;
		lastHumanActivityAt.set(null);
	}

	public Optional<Instant> lastHumanActivityAt() {
		return Optional.ofNullable(lastHumanActivityAt.get());
	}

	@Override
	public void close() {
		stop();
	}

	static boolean isQualifying(EventType<?> eventType) {
		return eventType == KeyEvent.KEY_PRESSED
				|| eventType == MouseEvent.MOUSE_PRESSED
				|| eventType == ScrollEvent.SCROLL
				|| eventType == TouchEvent.TOUCH_PRESSED;
	}

	void recordIfQualifying(EventType<?> eventType, boolean showing, boolean focused) {
		if (showing && focused && isQualifying(eventType)) lastHumanActivityAt.set(clock.instant());
	}

	boolean isStarted() {
		return started;
	}

	int registeredWindowCount() {
		return registrations.size();
	}

	private void refreshOwnedWindows() {
		if (!started) return;
		for (Window window : Window.getWindows()) {
			if (belongsToAuthenticatedContext(window) && !registrations.containsKey(window)) attach(window);
		}
		for (Window window : new ArrayList<>(registrations.keySet())) {
			if (!Window.getWindows().contains(window) || !belongsToAuthenticatedContext(window)) {
				EventHandler<Event> handler = registrations.remove(window);
				detach(window, handler);
			}
		}
	}

	private boolean belongsToAuthenticatedContext(Window candidate) {
		for (Window current = candidate; current != null; current = ownerOf(current)) {
			if (current == authenticatedWindow) return true;
		}
		return false;
	}

	private static Window ownerOf(Window window) {
		if (window instanceof Stage stage) return stage.getOwner();
		if (window instanceof PopupWindow popup) return popup.getOwnerWindow();
		return null;
	}

	private void attach(Window window) {
		EventHandler<Event> handler = event -> recordIfQualifying(event.getEventType(), window.isShowing(), window.isFocused());
		registrations.put(window, handler);
		window.addEventFilter(KeyEvent.KEY_PRESSED, handler);
		window.addEventFilter(MouseEvent.MOUSE_PRESSED, handler);
		window.addEventFilter(ScrollEvent.SCROLL, handler);
		window.addEventFilter(TouchEvent.TOUCH_PRESSED, handler);
	}

	private static void detach(Window window, EventHandler<Event> handler) {
		window.removeEventFilter(KeyEvent.KEY_PRESSED, handler);
		window.removeEventFilter(MouseEvent.MOUSE_PRESSED, handler);
		window.removeEventFilter(ScrollEvent.SCROLL, handler);
		window.removeEventFilter(TouchEvent.TOUCH_PRESSED, handler);
	}
}
