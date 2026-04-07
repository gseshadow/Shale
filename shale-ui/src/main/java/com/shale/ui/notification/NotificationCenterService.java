package com.shale.ui.notification;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.Consumer;

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
	private final ObservableList<AppNotification> notifications = FXCollections.observableArrayList();
	private final SortedList<AppNotification> notificationsNewestFirst = new SortedList<>(notifications,
			Comparator.comparing(AppNotification::getCreatedAt).reversed());
	private final ReadOnlyIntegerWrapper unreadCount = new ReadOnlyIntegerWrapper(0);
	private final ReadOnlyObjectWrapper<AppNotification> activeBanner = new ReadOnlyObjectWrapper<>();
	private Consumer<List<AppNotification>> readListener = ignored -> {};

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
		boolean exists = notifications.stream().anyMatch(existing -> isSameNotification(existing, notification));
		if (!exists) {
			notifications.add(notification);
		}
	}

	private static boolean isSameNotification(AppNotification left, AppNotification right) {
		if (left == null || right == null) {
			return false;
		}
		Long leftDurableId = left.getDurableNotificationId();
		Long rightDurableId = right.getDurableNotificationId();
		if (leftDurableId != null && rightDurableId != null) {
			return leftDurableId.equals(rightDurableId);
		}
		String leftEventKey = left.getEventKey();
		String rightEventKey = right.getEventKey();
		return leftEventKey != null && !leftEventKey.isBlank()
				&& rightEventKey != null
				&& leftEventKey.equals(rightEventKey);
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
			recomputeDerivedState();
		} else {
			Platform.runLater(() -> {
				notifications.clear();
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
		recomputeDerivedState();
		if (!changed.isEmpty()) {
			readListener.accept(changed);
		}
	}

	public void markAllRead() {
		markMatchingReadInternal(item -> true);
	}

	public void markRead(AppNotification notification) {
		if (notification != null && notification.isUnread()) {
			notification.setUnread(false);
			recomputeDerivedState();
			readListener.accept(List.of(notification));
		}
	}

	public void setReadListener(Consumer<List<AppNotification>> listener) {
		this.readListener = listener == null ? ignored -> {} : listener;
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
				.forEach(notifications::add);
		recomputeDerivedState();
	}

	private void recomputeDerivedState() {
		unreadCount.set((int) notifications.stream().filter(AppNotification::isUnread).count());
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
