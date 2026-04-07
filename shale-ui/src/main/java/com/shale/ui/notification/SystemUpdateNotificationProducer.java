package com.shale.ui.notification;

import com.shale.ui.services.UiUpdateLauncher;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

public final class SystemUpdateNotificationProducer {
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
			return;
		}
		if (!notificationPreferencesService.isEnabled(NotificationPreferenceKey.APP_UPDATES)) {
			return;
		}

		if (!result.updateAvailable()) {
			lastUpdateAvailable = false;
			lastMandatory = false;
			return;
		}

		if (Boolean.TRUE.equals(lastUpdateAvailable) && Objects.equals(lastMandatory, result.mandatory())) {
			return;
		}

		lastUpdateAvailable = true;
		lastMandatory = result.mandatory();

		boolean mandatory = result.mandatory();
		notificationCenterService.pushNotification(new AppNotification(
				"update-available-" + (mandatory ? "mandatory" : "optional"),
				NotificationCategory.APP_UPDATE,
				mandatory ? NotificationSeverity.CRITICAL : NotificationSeverity.WARNING,
				mandatory ? "Update required" : "Update available",
				mandatory
						? "A required Shale update is available before continuing regular use."
						: "A newer version of Shale is available.",
				Instant.now(clock),
				true,
				true,
				NotificationTargetScope.SESSION_SYSTEM));
	}

	public void onUpdaterLaunchSucceeded() {
		if (restartRequiredNotified) {
			return;
		}
		if (!notificationPreferencesService.isEnabled(NotificationPreferenceKey.APP_UPDATES)) {
			return;
		}
		restartRequiredNotified = true;
		notificationCenterService.pushNotification(new AppNotification(
				"update-restart-required",
				NotificationCategory.APP_UPDATE,
				NotificationSeverity.CRITICAL,
				"Restart required",
				"An update handoff started. Restart Shale after install completes.",
				Instant.now(clock),
				true,
				true,
				NotificationTargetScope.SESSION_SYSTEM));
	}
}
