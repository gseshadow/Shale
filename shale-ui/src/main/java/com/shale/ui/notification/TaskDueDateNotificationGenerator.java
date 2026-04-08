package com.shale.ui.notification;

import com.shale.data.dao.NotificationDao;
import com.shale.data.dao.TaskDao;
import com.shale.data.dao.TaskDao.TaskDueNotificationCandidate;
import com.shale.ui.state.AppState;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class TaskDueDateNotificationGenerator {
	private static final long CADENCE_MINUTES = 30;

	private final TaskDao taskDao;
	private final NotificationDao notificationDao;
	private final AppState appState;
	private final TaskDueNotificationRecipientResolver recipientResolver;
	private final Clock clock;
	private final ZoneId zoneId;
	private ScheduledExecutorService scheduler;

	public TaskDueDateNotificationGenerator(
			TaskDao taskDao,
			NotificationDao notificationDao,
			AppState appState,
			TaskDueNotificationRecipientResolver recipientResolver) {
		this(taskDao, notificationDao, appState, recipientResolver, Clock.systemDefaultZone(), ZoneId.systemDefault());
	}

	TaskDueDateNotificationGenerator(
			TaskDao taskDao,
			NotificationDao notificationDao,
			AppState appState,
			TaskDueNotificationRecipientResolver recipientResolver,
			Clock clock,
			ZoneId zoneId) {
		this.taskDao = Objects.requireNonNull(taskDao, "taskDao");
		this.notificationDao = Objects.requireNonNull(notificationDao, "notificationDao");
		this.appState = Objects.requireNonNull(appState, "appState");
		this.recipientResolver = Objects.requireNonNull(recipientResolver, "recipientResolver");
		this.clock = Objects.requireNonNull(clock, "clock");
		this.zoneId = Objects.requireNonNull(zoneId, "zoneId");
	}

	public void start() {
		if (scheduler != null && !scheduler.isShutdown()) {
			return;
		}
		scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
			Thread t = new Thread(r, "task-due-notification-generator");
			t.setDaemon(true);
			return t;
		});
		scheduler.scheduleAtFixedRate(this::runSafely, CADENCE_MINUTES, CADENCE_MINUTES, TimeUnit.MINUTES);
	}

	public void stop() {
		if (scheduler != null) {
			scheduler.shutdownNow();
			scheduler = null;
		}
	}

	public void runOnce() {
		runSafely();
	}

	private void runSafely() {
		try {
			Integer shaleClientId = appState.getShaleClientId();
			if (shaleClientId == null || shaleClientId <= 0) {
				return;
			}
			LocalDate today = LocalDate.now(clock.withZone(zoneId));
			List<TaskDueNotificationCandidate> candidates = taskDao.listDueNotificationCandidates(shaleClientId);
			for (TaskDueNotificationCandidate candidate : candidates) {
				DueState state = classifyDueState(candidate, today);
				if (state == null) {
					continue;
				}
				List<Integer> recipients = recipientResolver.resolveTaskDueNotificationRecipients(candidate);
				for (Integer recipientUserId : recipients) {
					if (recipientUserId == null || recipientUserId <= 0) {
						continue;
					}
						String eventKey = state.eventKey(candidate.taskId(), recipientUserId, today);
					notificationDao.createTaskDueDateNotification(
							candidate.shaleClientId(),
							recipientUserId,
							state.title(),
							state.message(candidate),
							candidate.taskId(),
							0,
							state.actionType(),
							state.severity(),
							eventKey);
				}
			}
		} catch (Exception ex) {
			System.err.println("Task due-date generator failed: " + ex.getMessage());
		}
	}

	private static DueState classifyDueState(TaskDueNotificationCandidate candidate, LocalDate today) {
		if (candidate == null || candidate.deleted() || candidate.completedAt() != null || candidate.dueAt() == null) {
			return null;
		}
		LocalDate dueDate = candidate.dueAt().toLocalDate();
		if (dueDate.isBefore(today)) {
			return DueState.OVERDUE;
		}
		if (dueDate.isEqual(today)) {
			return DueState.DUE_TODAY;
		}
		if (dueDate.isEqual(today.plusDays(1))) {
			return DueState.DUE_TOMORROW;
		}
		return null;
	}

		private enum DueState {
		OVERDUE("Task overdue", "DUE_OVERDUE", "WARNING", "task-overdue"),
		DUE_TODAY("Task due today", "DUE_TODAY", "INFO", "task-due-today"),
		DUE_TOMORROW("Task due tomorrow", "DUE_TOMORROW", "INFO", "task-due-tomorrow");

		private final String title;
		private final String actionType;
		private final String severity;
		private final String eventPrefix;

		DueState(String title, String actionType, String severity, String eventPrefix) {
			this.title = title;
			this.actionType = actionType;
			this.severity = severity;
			this.eventPrefix = eventPrefix;
		}

		String title() {
			return title;
		}

		String actionType() {
			return actionType;
		}

		String severity() {
			return severity;
		}

			String eventKey(long taskId, int userId, LocalDate today) {
				if (this == OVERDUE) {
					// Overdue reminders are intentionally one-time per task/user to avoid repeat noise.
					return eventPrefix + ':' + taskId + ':' + userId;
				}
				// Due-today and due-tomorrow keys include a day bucket to dedupe repeated scans within a day.
				return eventPrefix + ':' + taskId + ':' + userId + ':' + today;
			}

		String message(TaskDueNotificationCandidate candidate) {
			String taskTitle = candidate.title() == null || candidate.title().isBlank()
					? "Task #" + candidate.taskId()
					: candidate.title().trim();
			String dueDateText = candidate.dueAt() == null ? "" : candidate.dueAt().toLocalDate().toString();
			if (candidate.caseName() != null && !candidate.caseName().isBlank()) {
				return "Task: " + taskTitle + " • Case: " + candidate.caseName() + " • Due: " + dueDateText;
			}
			return "Task: " + taskTitle + " • Due: " + dueDateText;
		}
	}
}
