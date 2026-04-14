package com.shale.ui.notification;

import com.shale.ui.services.UiUpdateLauncher;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

public final class SystemUpdateNotificationProducer {
	private static final String UPDATE_AVAILABLE_ID_PREFIX = "update-available-";

	private final NotificationCenterService notificationCenterService;
	private final NotificationPreferencesService notificationPreferencesService;
	private final Clock clock;

	private Boolean lastUpdateAvailable;
	private Boolean lastMandatory;
	private boolean restartRequiredNotified;

	public SystemUpdateNotificationProducer(
			NotificationCenterService notificationCenterService,
			NotificationPreferencesService notificationPreferencesService) {
		this(notificationCenterService, notificationPreferencesService, Clock.systemUTC());
	}

	SystemUpdateNotificationProducer(
			NotificationCenterService notificationCenterService,
			NotificationPreferencesService notificationPreferencesService,
			Clock clock) {
		this.notificationCenterService = Objects.requireNonNull(notificationCenterService, "notificationCenterService");
		this.notificationPreferencesService = Objects.requireNonNull(notificationPreferencesService, "notificationPreferencesService");
		this.clock = Objects.requireNonNull(clock, "clock");
	}

	public void onUpdateCheckResult(UiUpdateLauncher.UpdateCheckResult result) {
		if (result == null) {
			log("Update check result skipped: result=<null>");
			return;
		}
		if (!notificationPreferencesService.isEnabled(NotificationPreferenceKey.APP_UPDATES)) {
			log("Update check result skipped: app update notifications are disabled");
			return;
		}

		log("Update check result: updateAvailable=" + result.updateAvailable() + ", mandatory=" + result.mandatory());
		if (!result.updateAvailable()) {
			lastUpdateAvailable = false;
			lastMandatory = false;
			boolean removed = removeAvailableUpdateNotification();
			log(removed
					? "Update notification row removed because app is up to date"
					: "Update notification row removal skipped: no existing row");
			return;
		}

		if (Boolean.TRUE.equals(lastUpdateAvailable) && Objects.equals(lastMandatory, result.mandatory())) {
			log("Update notification row skipped as duplicate for same availability state");
			return;
		}

		lastUpdateAvailable = true;
		lastMandatory = result.mandatory();

		boolean mandatory = result.mandatory();
		String notificationId = UPDATE_AVAILABLE_ID_PREFIX + (mandatory ? "mandatory" : "optional");
		boolean removedExisting = removeAvailableUpdateNotification();
		boolean showAsBanner = notificationPreferencesService.isEnabled(NotificationPreferenceKey.APP_UPDATES_BANNER);
		notificationCenterService.pushNotification(new AppNotification(
				notificationId,
				NotificationCategory.APP_UPDATE,
				mandatory ? NotificationSeverity.CRITICAL : NotificationSeverity.WARNING,
				mandatory ? "Update required" : "Update available",
				mandatory
						? "A required Shale update is available before continuing regular use."
						: "A newer version of Shale is available.",
				Instant.now(clock),
				true,
				showAsBanner,
				NotificationTargetScope.SESSION_SYSTEM));
		log(removedExisting
				? "Update notification row refreshed"
				: "Update notification row created");
	}

	public void onUpdaterLaunchSucceeded() {
		if (restartRequiredNotified) {
			return;
		}
		if (!notificationPreferencesService.isEnabled(NotificationPreferenceKey.APP_UPDATES)) {
			return;
		}
		restartRequiredNotified = true;
		boolean showAsBanner = notificationPreferencesService.isEnabled(NotificationPreferenceKey.APP_UPDATES_BANNER);
		notificationCenterService.pushNotification(new AppNotification(
				"update-restart-required",
				NotificationCategory.APP_UPDATE,
				NotificationSeverity.CRITICAL,
				"Restart required",
				"An update handoff started. Restart Shale after install completes.",
				Instant.now(clock),
				true,
				showAsBanner,
				NotificationTargetScope.SESSION_SYSTEM));
	}

	private boolean removeAvailableUpdateNotification() {
		int before = notificationCenterService.getNotificationsNewestFirst().size();
		notificationCenterService.clearMatching(notification -> notification != null
				&& notification.getCategory() == NotificationCategory.APP_UPDATE
				&& notification.getId() != null
				&& notification.getId().startsWith(UPDATE_AVAILABLE_ID_PREFIX));
		return notificationCenterService.getNotificationsNewestFirst().size() < before;
	}

	private static void log(String message) {
		System.out.println("[UpdateNotification] " + message);
	}
}
