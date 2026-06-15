package com.shale.ui.notification;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.application.Platform;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.ReadOnlyIntegerWrapper;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.transformation.SortedList;

public final class NotificationCenterService {
	private static final Logger log = LoggerFactory.getLogger(NotificationCenterService.class);

	private final ObservableList<AppNotification> notifications = FXCollections.observableArrayList();
	private final SortedList<AppNotification> notificationsNewestFirst = new SortedList<>(notifications,
			Comparator.comparing(AppNotification::getCreatedAt).reversed());
	private final ReadOnlyIntegerWrapper unreadCount = new ReadOnlyIntegerWrapper(0);
	private final ReadOnlyObjectWrapper<AppNotification> activeBanner = new ReadOnlyObjectWrapper<>();
	private final Set<Long> durableNotificationIds = new HashSet<>();
	private final Set<String> eventKeys = new HashSet<>();
	private Integer serverUnreadCount;
	private Consumer<List<AppNotification>> readListener = ignored -> {};
	private Consumer<List<AppNotification>> dismissListener = ignored -> {};

	public NotificationCenterService() {
		notifications.addListener((ListChangeListener<AppNotification>) change -> {
			while (change.next()) {
				if (change.wasAdded()) {
					for (AppNotification notification : change.getAddedSubList()) {
						notification.unreadProperty().addListener((obs, oldValue, newValue) -> recomputeDerivedState());
					}
				}
			}
			recomputeDerivedState();
		});
	}


	public static NotificationCenterService empty() {
		return new NotificationCenterService();
	}

	public static NotificationCenterService seeded(Clock clock) {
		NotificationCenterService service = new NotificationCenterService();
		service.seed(Objects.requireNonNull(clock, "clock"));
		return service;
	}

	public ObservableList<AppNotification> getNotificationsNewestFirst() {
		return notificationsNewestFirst;
	}

	public ReadOnlyIntegerProperty unreadCountProperty() {
		return unreadCount.getReadOnlyProperty();
	}

	public int getUnreadCount() {
		return unreadCount.get();
	}

	public void applyServerUnreadCount(int unreadCount) {
		int safeUnreadCount = Math.max(0, unreadCount);
		if (Platform.isFxApplicationThread()) {
			applyServerUnreadCountInternal(safeUnreadCount);
		} else {
			Platform.runLater(() -> applyServerUnreadCountInternal(safeUnreadCount));
		}
	}

	private void applyServerUnreadCountInternal(int unreadCount) {
		serverUnreadCount = unreadCount;
		recomputeDerivedState();
	}

	public void completeInitialHydration() {
		if (Platform.isFxApplicationThread()) {
			completeInitialHydrationInternal();
		} else {
			Platform.runLater(this::completeInitialHydrationInternal);
		}
	}

	private void completeInitialHydrationInternal() {
		serverUnreadCount = null;
		recomputeDerivedState();
	}

	public ReadOnlyObjectProperty<AppNotification> activeBannerProperty() {
		return activeBanner.getReadOnlyProperty();
	}

	public Optional<AppNotification> getActiveBanner() {
		return Optional.ofNullable(activeBanner.get());
	}


	public void pushNotification(AppNotification notification) {
		if (notification == null) {
			return;
		}
		if (Platform.isFxApplicationThread()) {
			pushNotificationInternal(notification);
		} else {
			Platform.runLater(() -> pushNotificationInternal(notification));
		}
	}

	private void pushNotificationInternal(AppNotification notification) {
		pushNotificationsInternal(List.of(notification), "single");
	}

	public void pushNotifications(List<AppNotification> notificationsToAdd) {
		if (notificationsToAdd == null || notificationsToAdd.isEmpty()) {
			return;
		}
		List<AppNotification> snapshot = notificationsToAdd.stream()
				.filter(Objects::nonNull)
				.toList();
		if (snapshot.isEmpty()) {
			return;
		}
		if (Platform.isFxApplicationThread()) {
			pushNotificationsInternal(snapshot, "bulk");
		} else {
			Platform.runLater(() -> pushNotificationsInternal(snapshot, "bulk"));
		}
	}

	private void pushNotificationsInternal(List<AppNotification> incoming, String source) {
		long startNanos = System.nanoTime();
		int added = 0;
		int duplicate = 0;
		int unreadAdded = 0;
		for (AppNotification notification : incoming) {
			if (isKnownNotification(notification)) {
				duplicate++;
				continue;
			}
			notifications.add(notification);
			indexNotification(notification);
			if (notification.isUnread()) {
				unreadAdded++;
			}
			added++;
		}
		if (serverUnreadCount != null && unreadAdded > 0) {
			serverUnreadCount += unreadAdded;
		}
		long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000;
		if (added > 0 || duplicate > 0) {
			log.info("PERF notifications.push source={} incoming={} added={} duplicates={} total={} elapsedMs={} fxThread={}",
					source, incoming.size(), added, duplicate, notifications.size(), elapsedMs, Platform.isFxApplicationThread());
		}
		String eventKey = notification.getEventKey();
		return eventKey != null && !eventKey.isBlank() && eventKeys.contains(eventKey);
	}

	private boolean isKnownNotification(AppNotification notification) {
		if (notification == null) {
			return true;
		}
		Long durableId = notification.getDurableNotificationId();
		if (durableId != null) {
			return durableNotificationIds.contains(durableId);
		}
		String eventKey = notification.getEventKey();
		return eventKey != null && !eventKey.isBlank() && eventKeys.contains(eventKey);
	}

	private void indexNotification(AppNotification notification) {
		Long durableId = notification.getDurableNotificationId();
		if (durableId != null) {
			durableNotificationIds.add(durableId);
		}
		String eventKey = notification.getEventKey();
		if (eventKey != null && !eventKey.isBlank()) {
			eventKeys.add(eventKey);
		}
	}

	public void markReadById(String notificationId) {
		if (notificationId == null || notificationId.isBlank()) {
			return;
		}
		markReadMatching(item -> notificationId.equals(item.getId()));
	}

	public void clearAll() {
		if (Platform.isFxApplicationThread()) {
			notifications.clear();
			durableNotificationIds.clear();
			eventKeys.clear();
			serverUnreadCount = null;
			recomputeDerivedState();
		} else {
			Platform.runLater(() -> {
				notifications.clear();
				durableNotificationIds.clear();
				eventKeys.clear();
				serverUnreadCount = null;
				recomputeDerivedState();
			});
		}
	}

	public void clearSessionScoped() {
		clearMatching(notification -> notification != null
				&& notification.getTargetScope() == NotificationTargetScope.SESSION_SYSTEM);
	}

	public void clearMatching(Predicate<AppNotification> predicate) {
		if (predicate == null) {
			return;
		}
		if (Platform.isFxApplicationThread()) {
			clearMatchingInternal(predicate);
		} else {
			Platform.runLater(() -> clearMatchingInternal(predicate));
		}
	}

	private void clearMatchingInternal(Predicate<AppNotification> predicate) {
		notifications.removeIf(predicate);
		recomputeDerivedState();
	}

	public void markReadMatching(Predicate<AppNotification> predicate) {
		if (predicate == null) {
			return;
		}
		if (Platform.isFxApplicationThread()) {
			markMatchingReadInternal(predicate);
		} else {
			Platform.runLater(() -> markMatchingReadInternal(predicate));
		}
	}

	private void markMatchingReadInternal(Predicate<AppNotification> predicate) {
		List<AppNotification> changed = notifications.stream()
				.filter(predicate)
				.filter(AppNotification::isUnread)
				.toList();
		changed.forEach(item -> item.setUnread(false));
		if (serverUnreadCount != null && !changed.isEmpty()) {
			serverUnreadCount = Math.max(0, serverUnreadCount - changed.size());
		}
		recomputeDerivedState();
		if (!changed.isEmpty()) {
			readListener.accept(changed);
		}
	}

	public void markAllRead() {
		markReadMatching(item -> true);
	}

	public void markRead(AppNotification notification) {
		if (notification != null && notification.isUnread()) {
			notification.setUnread(false);
			if (serverUnreadCount != null) {
				serverUnreadCount = Math.max(0, serverUnreadCount - 1);
			}
			recomputeDerivedState();
			readListener.accept(List.of(notification));
		}
	}

	public void setReadListener(Consumer<List<AppNotification>> listener) {
		this.readListener = listener == null ? ignored -> {} : listener;
	}

	public void setDismissListener(Consumer<List<AppNotification>> listener) {
		this.dismissListener = listener == null ? ignored -> {} : listener;
	}

	public void dismiss(AppNotification notification) {
		if (notification == null) {
			return;
		}
		if (Platform.isFxApplicationThread()) {
			dismissInternal(notification);
		} else {
			Platform.runLater(() -> dismissInternal(notification));
		}
	}

	public void dismissAll(List<AppNotification> notificationsToDismiss) {
		if (notificationsToDismiss == null || notificationsToDismiss.isEmpty()) {
			return;
		}
		List<AppNotification> snapshot = notificationsToDismiss.stream()
				.filter(Objects::nonNull)
				.distinct()
				.toList();
		if (snapshot.isEmpty()) {
			return;
		}
		if (Platform.isFxApplicationThread()) {
			dismissAllInternal(snapshot);
		} else {
			Platform.runLater(() -> dismissAllInternal(snapshot));
		}
	}

	public void dismissMatching(Predicate<AppNotification> predicate) {
		if (predicate == null) {
			return;
		}
		if (Platform.isFxApplicationThread()) {
			dismissMatchingInternal(predicate);
		} else {
			Platform.runLater(() -> dismissMatchingInternal(predicate));
		}
	}

	public void dismissRead() {
		dismissMatching(notification -> notification != null && !notification.isUnread());
	}

	public void dismissOlderThan(Instant cutoff) {
		if (cutoff == null) {
			return;
		}
		dismissMatching(notification -> notification != null && notification.getCreatedAt().isBefore(cutoff));
	}

	public void dismissById(String notificationId) {
		if (notificationId == null || notificationId.isBlank()) {
			return;
		}
		if (Platform.isFxApplicationThread()) {
			AppNotification target = notifications.stream()
					.filter(item -> notificationId.equals(item.getId()))
					.findFirst()
					.orElse(null);
			dismissInternal(target);
		} else {
			Platform.runLater(() -> dismissById(notificationId));
		}
	}

	private void dismissInternal(AppNotification notification) {
		if (notification == null) {
			return;
		}
		dismissAllInternal(List.of(notification));
	}

	private void dismissMatchingInternal(Predicate<AppNotification> predicate) {
		List<AppNotification> matched = notifications.stream()
				.filter(predicate)
				.toList();
		dismissAllInternal(matched);
	}

	private void dismissAllInternal(List<AppNotification> notificationsToDismiss) {
		if (notificationsToDismiss == null || notificationsToDismiss.isEmpty()) {
			return;
		}
		List<AppNotification> removed = notificationsToDismiss.stream()
				.filter(Objects::nonNull)
				.distinct()
				.filter(notifications::contains)
				.toList();
		if (removed.isEmpty()) {
			return;
		}
		long startNanos = System.nanoTime();
		long unreadRemoved = removed.stream().filter(AppNotification::isUnread).count();
		notifications.removeAll(removed);
		for (AppNotification notification : removed) {
			Long durableId = notification.getDurableNotificationId();
			if (durableId != null) {
				durableNotificationIds.remove(durableId);
			}
			String eventKey = notification.getEventKey();
			if (eventKey != null && !eventKey.isBlank()) {
				eventKeys.remove(eventKey);
			}
		}
		if (serverUnreadCount != null && unreadRemoved > 0) {
			serverUnreadCount = Math.max(0, serverUnreadCount - Math.toIntExact(unreadRemoved));
		}
		recomputeDerivedState();
		long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000;
		log.info("PERF notifications.dismiss removed={} remaining={} elapsedMs={} fxThread={}", removed.size(), notifications.size(), elapsedMs, Platform.isFxApplicationThread());
		dismissListener.accept(removed);
	}

	private void seed(Clock clock) {
		Instant now = Instant.now(clock);
		List.of(
				new AppNotification(
						UUID.randomUUID().toString(),
						NotificationCategory.SYSTEM,
						NotificationSeverity.INFO,
						"Application update available",
						"A new Shale desktop release is ready to install.",
						now.minus(15, ChronoUnit.MINUTES),
						true,
						true,
						NotificationTargetScope.SESSION_SYSTEM),
				new AppNotification(
						UUID.randomUUID().toString(),
						NotificationCategory.CONNECTIVITY,
						NotificationSeverity.WARNING,
						"Offline mode",
						"Connection to live services is degraded; data may be delayed.",
						now.minus(45, ChronoUnit.MINUTES),
						true,
						true,
						NotificationTargetScope.SESSION_SYSTEM),
				new AppNotification(
						UUID.randomUUID().toString(),
						NotificationCategory.TASK,
						NotificationSeverity.WARNING,
						"Task due tomorrow",
						"Prepare witness packet for Case #142 by tomorrow morning.",
						now.minus(2, ChronoUnit.HOURS),
						true,
						true,
						NotificationTargetScope.USER_SCOPED),
				new AppNotification(
						UUID.randomUUID().toString(),
						NotificationCategory.CASE,
						NotificationSeverity.INFO,
						"Case updated",
						"Case #87 has a new note from opposing counsel.",
						now.minus(1, ChronoUnit.DAYS),
						false,
						false,
						NotificationTargetScope.USER_SCOPED),
				new AppNotification(
						UUID.randomUUID().toString(),
						NotificationCategory.TASK,
						NotificationSeverity.CRITICAL,
						"Deadline approaching",
						"Filing deadline for Case #203 is in 48 hours.",
						now.minus(2, ChronoUnit.DAYS),
						true,
						false,
						NotificationTargetScope.USER_SCOPED))
				.forEach(notification -> {
					notifications.add(notification);
					indexNotification(notification);
				});
		recomputeDerivedState();
	}

	private void recomputeDerivedState() {
		int hydratedUnreadCount = (int) notifications.stream().filter(AppNotification::isUnread).count();
		unreadCount.set(serverUnreadCount == null ? hydratedUnreadCount : Math.max(serverUnreadCount, hydratedUnreadCount));
		activeBanner.set(notifications.stream()
				.filter(AppNotification::isShowAsBanner)
				.filter(AppNotification::isUnread)
				.max(Comparator
						.comparingInt((AppNotification item) -> severityRank(item.getSeverity()))
						.thenComparing(AppNotification::getCreatedAt))
				.orElse(null));
	}

	private static int severityRank(NotificationSeverity severity) {
		if (severity == null) {
			return 0;
		}
		return switch (severity) {
		case CRITICAL -> 3;
		case WARNING -> 2;
		case INFO -> 1;
		};
	}
}
