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
	private final NotificationPreferencesService notificationPreferencesService;
	private final Clock clock;
	private final Map<String, Instant> recentEventKeys = new LinkedHashMap<>();
	private final Consumer<UiRuntimeBridge.EntityUpdatedEvent> eventHandler = this::handleEntityUpdated;

	private boolean subscribed;

	public LiveUpdateNotificationBridge(
			UiRuntimeBridge runtimeBridge,
			AppState appState,
			NotificationCenterService notificationCenterService,
			NotificationPreferencesService notificationPreferencesService) {
		this(runtimeBridge, appState, notificationCenterService, notificationPreferencesService, Clock.systemUTC());
	}

	LiveUpdateNotificationBridge(
			UiRuntimeBridge runtimeBridge,
			AppState appState,
			NotificationCenterService notificationCenterService,
			NotificationPreferencesService notificationPreferencesService,
			Clock clock) {
		this.runtimeBridge = Objects.requireNonNull(runtimeBridge, "runtimeBridge");
		this.appState = Objects.requireNonNull(appState, "appState");
		this.notificationCenterService = Objects.requireNonNull(notificationCenterService, "notificationCenterService");
		this.notificationPreferencesService = Objects.requireNonNull(notificationPreferencesService, "notificationPreferencesService");
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
		if (isSelfAssignment(event)) {
			return;
		}
		if (isDuplicate(event)) {
			return;
		}
		if (!notificationPreferencesService.isEnabled(NotificationPreferenceKey.TASK_ASSIGNED_TO_ME)) {
			return;
		}

		Instant createdAt = parseTimestamp(event.timestamp());
		String title = "Task assigned to you";
		String message = taskNotificationMessage(event);
		Integer durableIdInt = intValue(event.patch().get("durableNotificationId"));
		Long durableNotificationId = durableIdInt == null ? null : Long.valueOf(durableIdInt.longValue());
		Object eventKeyValue = event.patch().get("eventKey");
		String eventKey = eventKeyValue == null ? null : String.valueOf(eventKeyValue);
		notificationCenterService.pushNotification(new AppNotification(
				event.eventId() == null || event.eventId().isBlank()
						? "task-" + event.entityId() + "-" + createdAt.toEpochMilli()
						: event.eventId(),
					NotificationCategory.TASK,
					NotificationSeverity.INFO,
					title,
					message,
					createdAt,
					true,
					true,
					NotificationTargetScope.USER_SCOPED,
					durableNotificationId,
					eventKey));
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

		Integer newAssigneeUserId = assigneeUserId(event.patch());
		if (newAssigneeUserId == null || !newAssigneeUserId.equals(currentUserId)) {
			return false;
		}
		Integer previousAssigneeUserId = previousAssigneeUserId(event.patch());
		return previousAssigneeUserId == null || !previousAssigneeUserId.equals(currentUserId);
	}

	private boolean isSelfAssignment(UiRuntimeBridge.EntityUpdatedEvent event) {
		Integer currentUserId = appState.getUserId();
		return currentUserId != null
				&& currentUserId > 0
				&& currentUserId.equals(event.updatedByUserId());
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

	private static Integer assigneeUserId(Map<String, Object> patch) {
		if (patch == null) {
			return null;
		}
		Integer assigneeId = intValue(patch.get("assigneeUserId"));
		if (assigneeId == null) {
			assigneeId = intValue(patch.get("assignedUserId"));
		}
		if (assigneeId == null) {
			assigneeId = intValue(patch.get("userId"));
		}
		return assigneeId;
	}

	private static Integer previousAssigneeUserId(Map<String, Object> patch) {
		if (patch == null) {
			return null;
		}
		Integer previous = intValue(patch.get("previousAssigneeUserId"));
		if (previous == null) {
			previous = intValue(patch.get("oldAssigneeUserId"));
		}
		if (previous == null) {
			previous = intValue(patch.get("previousAssignedUserId"));
		}
		return previous;
	}

	private static String taskNotificationMessage(UiRuntimeBridge.EntityUpdatedEvent event) {
		Object titleValue = event.patch().get("title");
		String title = titleValue == null ? "" : String.valueOf(titleValue).trim();
		if (!title.isBlank()) {
			Object caseName = event.patch().get("caseName");
			if (caseName != null && !String.valueOf(caseName).isBlank()) {
				return "Task: " + title + " • Case: " + caseName;
			}
			Object caseId = event.patch().get("caseId");
			if (caseId != null) {
				return "Task: " + title + " • Case #" + caseId;
			}
			return "Task: " + title;
		}
		return "Task #" + event.entityId() + " was assigned to your queue.";
	}
}
