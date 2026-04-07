package com.shale.ui.notification;

import com.shale.ui.services.UiRuntimeBridge;
import com.shale.ui.state.AppState;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

public final class LiveUpdateNotificationBridge {
	private static final int DEDUPE_LIMIT = 300;
	private static final long DEDUPE_WINDOW_SECONDS = 120;

	private final UiRuntimeBridge runtimeBridge;
	private final AppState appState;
	private final NotificationCenterService notificationCenterService;
	private final Clock clock;
	private final Map<String, Instant> recentEventKeys = new LinkedHashMap<>();
	private final Consumer<UiRuntimeBridge.EntityUpdatedEvent> eventHandler = this::handleEntityUpdated;

	private boolean subscribed;

	public LiveUpdateNotificationBridge(
			UiRuntimeBridge runtimeBridge,
			AppState appState,
			NotificationCenterService notificationCenterService) {
		this(runtimeBridge, appState, notificationCenterService, Clock.systemUTC());
	}

	LiveUpdateNotificationBridge(
			UiRuntimeBridge runtimeBridge,
			AppState appState,
			NotificationCenterService notificationCenterService,
			Clock clock) {
		this.runtimeBridge = Objects.requireNonNull(runtimeBridge, "runtimeBridge");
		this.appState = Objects.requireNonNull(appState, "appState");
		this.notificationCenterService = Objects.requireNonNull(notificationCenterService, "notificationCenterService");
		this.clock = Objects.requireNonNull(clock, "clock");
	}

	public void start() {
		if (subscribed) {
			return;
		}
		runtimeBridge.subscribeEntityUpdated(eventHandler);
		subscribed = true;
	}

	public void stop() {
		if (!subscribed) {
			return;
		}
		runtimeBridge.unsubscribeEntityUpdated(eventHandler);
		subscribed = false;
		recentEventKeys.clear();
	}

	private void handleEntityUpdated(UiRuntimeBridge.EntityUpdatedEvent event) {
		if (!isTaskEventForCurrentUser(event)) {
			return;
		}
		if (isOwnEcho(event)) {
			return;
		}
		if (isDuplicate(event)) {
			return;
		}

		Instant createdAt = parseTimestamp(event.timestamp());
		String title = isDueSoon(event.patch()) ? "Task due soon" : "Task assigned to you";
		String message = taskNotificationMessage(event);
		notificationCenterService.pushNotification(new AppNotification(
				event.eventId() == null || event.eventId().isBlank()
						? "task-" + event.entityId() + "-" + createdAt.toEpochMilli()
						: event.eventId(),
				NotificationCategory.TASK,
				isDueSoon(event.patch()) ? NotificationSeverity.WARNING : NotificationSeverity.INFO,
				title,
				message,
				createdAt,
				true,
				true));
	}

	private boolean isTaskEventForCurrentUser(UiRuntimeBridge.EntityUpdatedEvent event) {
		if (event == null || event.entityType() == null || event.patch() == null) {
			return false;
		}
		if (!"Task".equalsIgnoreCase(event.entityType())) {
			return false;
		}
		Integer currentUserId = appState.getUserId();
		if (currentUserId == null || currentUserId <= 0) {
			return false;
		}

		Integer assigneeId = intValue(event.patch().get("assigneeUserId"));
		if (assigneeId == null) {
			assigneeId = intValue(event.patch().get("assignedUserId"));
		}
		if (assigneeId == null) {
			assigneeId = intValue(event.patch().get("userId"));
		}
		if (assigneeId == null) {
			return false;
		}
		return assigneeId.equals(currentUserId);
	}

	private boolean isOwnEcho(UiRuntimeBridge.EntityUpdatedEvent event) {
		String mine = runtimeBridge.getClientInstanceId();
		return mine != null && !mine.isBlank() && mine.equals(event.clientInstanceId());
	}

	private boolean isDuplicate(UiRuntimeBridge.EntityUpdatedEvent event) {
		Instant now = Instant.now(clock);
		String key = dedupeKey(event);
		pruneOldEntries(now);
		if (recentEventKeys.containsKey(key)) {
			return true;
		}
		recentEventKeys.put(key, now);
		if (recentEventKeys.size() > DEDUPE_LIMIT) {
			Iterator<String> iterator = recentEventKeys.keySet().iterator();
			if (iterator.hasNext()) {
				iterator.next();
				iterator.remove();
			}
		}
		return false;
	}

	private String dedupeKey(UiRuntimeBridge.EntityUpdatedEvent event) {
		if (event.eventId() != null && !event.eventId().isBlank()) {
			return "eventId:" + event.eventId();
		}
		return event.entityType() + ':' + event.entityId() + ':' + Objects.toString(event.timestamp(), "") + ':'
				+ event.updatedByUserId();
	}

	private void pruneOldEntries(Instant now) {
		Iterator<Map.Entry<String, Instant>> iterator = recentEventKeys.entrySet().iterator();
		while (iterator.hasNext()) {
			Map.Entry<String, Instant> entry = iterator.next();
			if (entry.getValue().isBefore(now.minusSeconds(DEDUPE_WINDOW_SECONDS))) {
				iterator.remove();
			}
		}
	}

	private static Integer intValue(Object value) {
		if (value instanceof Number number) {
			return number.intValue();
		}
		if (value instanceof String text) {
			try {
				return Integer.parseInt(text.trim());
			} catch (NumberFormatException ignored) {
				return null;
			}
		}
		return null;
	}

	private static Instant parseTimestamp(String raw) {
		if (raw == null || raw.isBlank()) {
			return Instant.now();
		}
		try {
			return OffsetDateTime.parse(raw).toInstant();
		} catch (DateTimeParseException ignored) {
			return Instant.now();
		}
	}

	private static boolean isDueSoon(Map<String, Object> patch) {
		if (patch == null) {
			return false;
		}
		Object dueSoon = patch.get("dueSoon");
		if (dueSoon instanceof Boolean bool) {
			return bool;
		}
		Object dueAt = patch.get("dueAtUtc");
		if (dueAt == null) {
			dueAt = patch.get("dueAt");
		}
		if (!(dueAt instanceof String dueText) || dueText.isBlank()) {
			return false;
		}
		try {
			Instant due = OffsetDateTime.parse(dueText).toInstant();
			Instant now = Instant.now();
			return !due.isBefore(now.minusSeconds(60)) && due.isBefore(now.plusSeconds(48 * 3600));
		} catch (DateTimeParseException ignored) {
			return false;
		}
	}

	private static String taskNotificationMessage(UiRuntimeBridge.EntityUpdatedEvent event) {
		Object titleValue = event.patch().get("title");
		String title = titleValue == null ? "" : String.valueOf(titleValue).trim();
		if (!title.isBlank()) {
			return "Task: " + title;
		}
		return "Task #" + event.entityId() + " was assigned to your queue.";
	}
}
