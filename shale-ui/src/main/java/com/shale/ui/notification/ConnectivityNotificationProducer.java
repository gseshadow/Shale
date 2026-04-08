package com.shale.ui.notification;

import com.shale.ui.services.UiRuntimeBridge;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.function.Consumer;

public final class ConnectivityNotificationProducer {
	private final UiRuntimeBridge runtimeBridge;
	private final NotificationCenterService notificationCenterService;
	private final NotificationPreferencesService notificationPreferencesService;
	private final Clock clock;
	private final Consumer<UiRuntimeBridge.ConnectivityEvent> handler = this::onConnectivityChanged;
	private static final Duration OFFLINE_TRANSITION_DEBOUNCE = Duration.ofSeconds(5);

	private boolean started;
	private Boolean onlineState;
	private boolean baselineOnlineEstablished;
	private Instant lastOnlineAt;
	private String activeOfflineNotificationId;
	private boolean offlineNotificationActive;

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
		baselineOnlineEstablished = false;
		lastOnlineAt = null;
		activeOfflineNotificationId = null;
		offlineNotificationActive = false;
	}

	private void onConnectivityChanged(UiRuntimeBridge.ConnectivityEvent event) {
		if (event == null) {
			return;
		}
		boolean online = event.online();
		if (onlineState != null && onlineState.booleanValue() == online) {
			return;
		}
		onlineState = online;
		if (online) {
			handleOnline(event);
			return;
		}
		handleOffline(event);
	}

	private void handleOffline(UiRuntimeBridge.ConnectivityEvent event) {
		if (!baselineOnlineEstablished || !Boolean.FALSE.equals(onlineState)) {
			return;
		}
		if (isSuppressedOfflineDetail(event.detail())) {
			return;
		}
		Instant now = Instant.now(clock);
		if (lastOnlineAt != null && Duration.between(lastOnlineAt, now).compareTo(OFFLINE_TRANSITION_DEBOUNCE) < 0) {
			return;
		}
		if (!notificationPreferencesService.isEnabled(NotificationPreferenceKey.CONNECTIVITY_STATUS)) {
			offlineNotificationActive = true;
			return;
		}
		boolean showAsBanner = notificationPreferencesService.isEnabled(NotificationPreferenceKey.CONNECTIVITY_BANNER);
		activeOfflineNotificationId = "offline-" + now.toEpochMilli();
		notificationCenterService.pushNotification(new AppNotification(
				activeOfflineNotificationId,
				NotificationCategory.NETWORK,
				NotificationSeverity.CRITICAL,
				"Offline",
				"Connection lost to live services." + suffix(event.detail()),
				now,
				true,
				showAsBanner,
				NotificationTargetScope.SESSION_SYSTEM));
		offlineNotificationActive = true;
	}

	private void handleOnline(UiRuntimeBridge.ConnectivityEvent event) {
		Instant now = Instant.now(clock);
		lastOnlineAt = now;
		if (!baselineOnlineEstablished) {
			baselineOnlineEstablished = true;
			return;
		}
		if (activeOfflineNotificationId != null) {
			notificationCenterService.markReadById(activeOfflineNotificationId);
			activeOfflineNotificationId = null;
		}
		notificationCenterService.markReadMatching(this::isOfflineBannerNotification);
		if (!offlineNotificationActive) {
			return;
		}
		offlineNotificationActive = false;
		if (!notificationPreferencesService.isEnabled(NotificationPreferenceKey.CONNECTIVITY_STATUS)) {
			return;
		}
		boolean showAsBanner = notificationPreferencesService.isEnabled(NotificationPreferenceKey.CONNECTIVITY_BANNER);
		notificationCenterService.pushNotification(new AppNotification(
				"online-" + now.toEpochMilli(),
				NotificationCategory.NETWORK,
				NotificationSeverity.INFO,
				"Back online",
				"Connection restored to live services." + suffix(event.detail()),
				now,
				true,
				showAsBanner,
				NotificationTargetScope.SESSION_SYSTEM));
	}

	private static boolean isSuppressedOfflineDetail(String detail) {
		if (detail == null) {
			return false;
		}
		String normalized = detail.toLowerCase();
		return normalized.contains("logout")
				|| normalized.contains("signout")
				|| normalized.contains("shutdown");
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
