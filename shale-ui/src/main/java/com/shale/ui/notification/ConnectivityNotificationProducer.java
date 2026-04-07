package com.shale.ui.notification;

import com.shale.ui.services.UiRuntimeBridge;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.function.Consumer;

public final class ConnectivityNotificationProducer {
	private final UiRuntimeBridge runtimeBridge;
	private final NotificationCenterService notificationCenterService;
	private final NotificationPreferencesService notificationPreferencesService;
	private final Clock clock;
	private final Consumer<UiRuntimeBridge.ConnectivityEvent> handler = this::onConnectivityChanged;

	private boolean started;
	private Boolean onlineState;
	private boolean hasSeenOnline;
	private String activeOfflineNotificationId;

	public ConnectivityNotificationProducer(
			UiRuntimeBridge runtimeBridge,
			NotificationCenterService notificationCenterService,
			NotificationPreferencesService notificationPreferencesService) {
		this(runtimeBridge, notificationCenterService, notificationPreferencesService, Clock.systemUTC());
	}

	ConnectivityNotificationProducer(
			UiRuntimeBridge runtimeBridge,
			NotificationCenterService notificationCenterService,
			NotificationPreferencesService notificationPreferencesService,
			Clock clock) {
		this.runtimeBridge = Objects.requireNonNull(runtimeBridge, "runtimeBridge");
		this.notificationCenterService = Objects.requireNonNull(notificationCenterService, "notificationCenterService");
		this.notificationPreferencesService = Objects.requireNonNull(notificationPreferencesService, "notificationPreferencesService");
		this.clock = Objects.requireNonNull(clock, "clock");
	}

	public void start() {
		if (started) {
			return;
		}
		runtimeBridge.subscribeConnectivity(handler);
		started = true;
	}

	public void stop() {
		if (!started) {
			return;
		}
		runtimeBridge.unsubscribeConnectivity(handler);
		started = false;
		onlineState = null;
		hasSeenOnline = false;
		activeOfflineNotificationId = null;
	}

	private void onConnectivityChanged(UiRuntimeBridge.ConnectivityEvent event) {
		if (event == null) {
			return;
		}
		boolean online = event.online();
		if (onlineState != null && onlineState.booleanValue() == online) {
			return;
		}
		boolean wasOnline = Boolean.TRUE.equals(onlineState);
		onlineState = online;
		if (online) {
			hasSeenOnline = true;
			handleOnline(event);
			return;
		}
		if (!hasSeenOnline || !wasOnline) {
			return;
		}
		handleOffline(event);
	}

	private void handleOffline(UiRuntimeBridge.ConnectivityEvent event) {
		if (!notificationPreferencesService.isEnabled(NotificationPreferenceKey.CONNECTIVITY_STATUS)) {
			return;
		}
		activeOfflineNotificationId = "offline-" + Instant.now(clock).toEpochMilli();
		notificationCenterService.pushNotification(new AppNotification(
				activeOfflineNotificationId,
				NotificationCategory.NETWORK,
				NotificationSeverity.CRITICAL,
				"Offline",
				"Connection lost to live services." + suffix(event.detail()),
				Instant.now(clock),
				true,
				true,
				NotificationTargetScope.SESSION_SYSTEM));
	}

	private void handleOnline(UiRuntimeBridge.ConnectivityEvent event) {
		if (activeOfflineNotificationId != null) {
			notificationCenterService.markReadById(activeOfflineNotificationId);
			activeOfflineNotificationId = null;
		}
		notificationCenterService.markReadMatching(this::isOfflineBannerNotification);
		if (!notificationPreferencesService.isEnabled(NotificationPreferenceKey.CONNECTIVITY_STATUS)) {
			return;
		}
		notificationCenterService.pushNotification(new AppNotification(
				"online-" + Instant.now(clock).toEpochMilli(),
				NotificationCategory.NETWORK,
				NotificationSeverity.INFO,
				"Back online",
				"Connection restored to live services." + suffix(event.detail()),
				Instant.now(clock),
				true,
				false,
				NotificationTargetScope.SESSION_SYSTEM));
	}

	private boolean isOfflineBannerNotification(AppNotification notification) {
		if (notification == null || !notification.isUnread() || !notification.isShowAsBanner()) {
			return false;
		}
		String title = notification.getTitle();
		boolean looksOffline = title != null && title.toLowerCase().startsWith("offline");
		return looksOffline
				&& (notification.getCategory() == NotificationCategory.NETWORK
						|| notification.getCategory() == NotificationCategory.CONNECTIVITY);
	}

	private static String suffix(String detail) {
		if (detail == null || detail.isBlank()) {
			return "";
		}
		return " (" + detail.trim() + ')';
	}
}
