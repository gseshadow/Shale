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
		if (!isTaskNotificationEventForCurrentUser(event)) {
			return;
		}
		if (isSelfNotificationEvent(event)) {
			return;
		}
		if (isDuplicate(event)) {
			return;
		}
		if (!notificationPreferencesService.isEnabled(NotificationPreferenceKey.TASK_ASSIGNED_TO_ME)) {
			return;
		}

		Instant createdAt = parseTimestamp(event.timestamp());
		boolean noteAdded = isTaskNoteAddedEvent(event);
		String title = noteAdded ? "New note added to task" : "Task assigned to you";
		String message = noteAdded ? taskNoteNotificationMessage(event) : taskNotificationMessage(event);
		Integer durableIdInt = intValue(event.patch().get("durableNotificationId"));
		Long durableNotificationId = durableIdInt == null ? null : Long.valueOf(durableIdInt.longValue());
		Long entityId = longValue(event.patch().get("taskId"));
		if (entityId == null || entityId <= 0) {
			entityId = event.entityId() > 0 ? event.entityId() : null;
		}
		Object eventKeyValue = event.patch().get("eventKey");
		String eventKey = eventKeyValue == null ? null : String.valueOf(eventKeyValue);
		String taskTitle = stringValue(event.patch().get("title"));
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
					eventKey,
					"Task",
					entityId,
					taskTitle));
	}

	private boolean isTaskNotificationEventForCurrentUser(UiRuntimeBridge.EntityUpdatedEvent event) {
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

		if (isTaskNoteAddedEvent(event)) {
			Integer recipientUserId = recipientUserId(event.patch());
			return recipientUserId != null && recipientUserId.equals(currentUserId);
		}
		Integer newAssigneeUserId = assigneeUserId(event.patch());
		if (newAssigneeUserId == null || !newAssigneeUserId.equals(currentUserId)) {
			return false;
		}
		Integer previousAssigneeUserId = previousAssigneeUserId(event.patch());
		return previousAssigneeUserId == null || !previousAssigneeUserId.equals(currentUserId);
	}

	private boolean isSelfNotificationEvent(UiRuntimeBridge.EntityUpdatedEvent event) {
		Integer currentUserId = appState.getUserId();
		if (currentUserId == null || currentUserId <= 0) {
			return false;
		}
		if (isTaskNoteAddedEvent(event)) {
			return currentUserId.equals(event.updatedByUserId());
		}
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

	private static Long longValue(Object value) {
		if (value instanceof Number number) {
			return number.longValue();
		}
		if (value instanceof String text) {
			try {
				return Long.parseLong(text.trim());
			} catch (NumberFormatException ignored) {
				return null;
			}
		}
		return null;
	}

	private static String stringValue(Object value) {
		if (value == null) {
			return null;
		}
		String text = String.valueOf(value).trim();
		return text.isBlank() ? null : text;
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

	private static Integer recipientUserId(Map<String, Object> patch) {
		if (patch == null) {
			return null;
		}
		Integer recipient = intValue(patch.get("recipientUserId"));
		if (recipient == null) {
			recipient = intValue(patch.get("userId"));
		}
		return recipient;
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

	private static boolean isTaskNoteAddedEvent(UiRuntimeBridge.EntityUpdatedEvent event) {
		if (event == null || event.patch() == null) {
			return false;
		}
		Object notificationType = event.patch().get("notificationType");
		if (notificationType != null && "TASK_NOTE_ADDED".equalsIgnoreCase(String.valueOf(notificationType))) {
			return true;
		}
		Object actionType = event.patch().get("actionType");
		return actionType != null && "NOTE_ADDED".equalsIgnoreCase(String.valueOf(actionType));
	}

	private static String taskNoteNotificationMessage(UiRuntimeBridge.EntityUpdatedEvent event) {
		String base = taskNotificationMessage(event);
		Object snippet = event.patch().get("noteSnippet");
		if (snippet == null) {
			return base + " • New note added";
		}
		String normalized = String.valueOf(snippet).trim();
		if (normalized.isBlank()) {
			return base + " • New note added";
		}
		return base + " • Note: " + normalized;
	}
}
