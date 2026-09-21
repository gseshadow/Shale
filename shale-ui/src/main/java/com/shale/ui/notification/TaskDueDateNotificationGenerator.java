package com.shale.ui.notification;

import com.shale.data.dao.NotificationDao;
import com.shale.data.dao.MaterialRequestDao;
import com.shale.data.dao.MaterialRequestDao.MaterialRequestDueNotificationCandidate;
import com.shale.data.dao.MaterialRequestDao.MaterialRequestFollowUpNotificationCandidate;
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
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class TaskDueDateNotificationGenerator {
	private static final Logger log = LoggerFactory.getLogger(TaskDueDateNotificationGenerator.class);
	private static final long CADENCE_MINUTES = 30;

	private final TaskDao taskDao;
	private final MaterialRequestDao materialRequestDao;
	private final NotificationDao notificationDao;
	private final AppState appState;
	private final NotificationPreferencesService notificationPreferencesService;
	private final TaskDueNotificationRecipientResolver recipientResolver;
	private final Clock clock;
	private final ZoneId zoneId;
	private ScheduledExecutorService scheduler;
	private ScheduledFuture<?> scheduled;
	private long generation;
	private Session session;

	private record Session(int tenantId, int userId, long generation) {}

	public TaskDueDateNotificationGenerator(
			TaskDao taskDao,
			MaterialRequestDao materialRequestDao,
			NotificationDao notificationDao,
			AppState appState,
			NotificationPreferencesService notificationPreferencesService,
			TaskDueNotificationRecipientResolver recipientResolver) {
		this(taskDao, materialRequestDao, notificationDao, appState, notificationPreferencesService, recipientResolver, Clock.systemDefaultZone(), ZoneId.systemDefault());
	}

	TaskDueDateNotificationGenerator(
			TaskDao taskDao,
			MaterialRequestDao materialRequestDao,
			NotificationDao notificationDao,
			AppState appState,
			NotificationPreferencesService notificationPreferencesService,
			TaskDueNotificationRecipientResolver recipientResolver,
			Clock clock,
			ZoneId zoneId) {
		this.taskDao = Objects.requireNonNull(taskDao, "taskDao");
		this.materialRequestDao = Objects.requireNonNull(materialRequestDao, "materialRequestDao");
		this.notificationDao = Objects.requireNonNull(notificationDao, "notificationDao");
		this.appState = Objects.requireNonNull(appState, "appState");
		this.notificationPreferencesService = Objects.requireNonNull(notificationPreferencesService, "notificationPreferencesService");
		this.recipientResolver = Objects.requireNonNull(recipientResolver, "recipientResolver");
		this.clock = Objects.requireNonNull(clock, "clock");
		this.zoneId = Objects.requireNonNull(zoneId, "zoneId");
	}

	public synchronized void start() {
		if (scheduler != null && !scheduler.isShutdown()) {
			return;
		}
		Integer tenantId = appState.getShaleClientId();
		Integer userId = appState.getUserId();
		if (tenantId == null || tenantId <= 0 || userId == null || userId <= 0) return;
		long token = ++generation;
		session = new Session(tenantId, userId, token);
		scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
			Thread t = new Thread(r, "task-due-notification-generator");
			t.setDaemon(true);
			return t;
		});
		scheduled = scheduler.scheduleAtFixedRate(() -> runSafely(token), CADENCE_MINUTES, CADENCE_MINUTES, TimeUnit.MINUTES);
	}

	public synchronized void stop() {
		generation++;
		session = null;
		if (scheduled != null) {
			scheduled.cancel(true);
			scheduled = null;
		}
		if (scheduler != null) {
			scheduler.shutdownNow();
			scheduler = null;
		}
	}

	public void runOnce() {
		long token;
		synchronized (this) {
			if (session == null) return;
			token = session.generation();
		}
		runSafely(token);
	}

	private void runSafely(long token) {
		try {
			Session captured = activeSession(token);
			if (captured == null) return;
			int shaleClientId = captured.tenantId();
			LocalDate today = LocalDate.now(clock.withZone(zoneId));
			if (!active(token)) return;
			List<TaskDueNotificationCandidate> candidates = taskDao.listDueNotificationCandidates(shaleClientId);
			for (TaskDueNotificationCandidate candidate : candidates) {
				if (!active(token)) return;
				DueState state = classifyDueState(candidate, today);
				if (state == null) {
					continue;
				}
				List<Integer> recipients = recipientResolver.resolveTaskDueNotificationRecipients(candidate);
				for (Integer recipientUserId : recipients) {
					if (!active(token)) return;
					if (recipientUserId == null || recipientUserId <= 0) {
						continue;
					}
				if (!isDueStateEnabled(state, recipientUserId)) {
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
			if (!active(token)) return;
			for (MaterialRequestDueNotificationCandidate candidate : materialRequestDao.listDueNotificationCandidates(shaleClientId, today)) {
				if (!active(token)) return;
				for (Integer recipient : candidate.recipientUserIds()) {
					if (!active(token)) return;
					String eventKey = "material-request:" + candidate.requestId() + ":due:" + candidate.dueAt() + ":" + recipient;
					notificationDao.createMaterialRequestDueNotification(candidate.shaleClientId(), recipient,
							"Material request due", "A material request is due: " + candidate.title(), candidate.requestId(), eventKey);
				}
			}
			LocalDateTime now=LocalDateTime.now(clock.withZone(zoneId));
			if (!active(token)) return;
			for(MaterialRequestFollowUpNotificationCandidate candidate:materialRequestDao.listFollowUpNotificationCandidates(shaleClientId,now)){
				if (!active(token)) return;
				for(Integer recipient:candidate.recipientUserIds()){
					if (!active(token)) return;
					String eventKey="material-request:"+candidate.requestId()+":follow-up:"+candidate.nextFollowUpAt()+":"+recipient;
					notificationDao.createMaterialRequestFollowUpNotification(candidate.shaleClientId(),recipient,"Material request follow-up",
							"Follow up on "+candidate.title()+" in its case.",candidate.requestId(),eventKey);
				}
			}
		} catch (RuntimeException ex) {
			if (active(token)) {
				log.error("Task due-date generator failed for active session", ex);
			}
		}
	}

	private synchronized Session activeSession(long token) {
		if (session == null || generation != token || session.generation() != token) return null;
		Integer tenantId = appState.getShaleClientId();
		Integer userId = appState.getUserId();
		return Objects.equals(tenantId, session.tenantId()) && Objects.equals(userId, session.userId()) ? session : null;
	}

	private boolean active(long token) {
		return activeSession(token) != null && !Thread.currentThread().isInterrupted();
	}

	private boolean isDueStateEnabled(DueState state, int recipientUserId) {
		Integer currentUserId = appState.getUserId();
		if (currentUserId == null || currentUserId <= 0 || currentUserId != recipientUserId) {
			return true;
		}
		return switch (state) {
			case OVERDUE -> notificationPreferencesService.isEnabled(NotificationPreferenceKey.TASK_DUE_OVERDUE);
			case DUE_TODAY -> notificationPreferencesService.isEnabled(NotificationPreferenceKey.TASK_DUE_TODAY);
			case DUE_TOMORROW -> notificationPreferencesService.isEnabled(NotificationPreferenceKey.TASK_DUE_TOMORROW);
		};
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
			return switch (this) {
				case OVERDUE -> "A task assigned to you is overdue.";
				case DUE_TODAY -> "A task assigned to you is due today.";
				case DUE_TOMORROW -> "A task assigned to you is due tomorrow.";
			};
		}
	}
}
