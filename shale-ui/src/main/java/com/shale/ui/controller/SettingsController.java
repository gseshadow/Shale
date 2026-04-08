package com.shale.ui.controller;

import com.shale.ui.notification.NotificationPreferenceKey;
import com.shale.ui.notification.NotificationPreferences;
import com.shale.ui.notification.NotificationPreferencesService;
import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

public final class SettingsController {
	@FXML
	private CheckBox taskAssignedToMeCheck;
	@FXML
	private CheckBox taskOverdueCheck;
	@FXML
	private CheckBox taskDueTodayCheck;
	@FXML
	private CheckBox taskDueTomorrowCheck;
	@FXML
	private CheckBox appUpdatesCheck;
	@FXML
	private CheckBox connectivityCheck;
	@FXML
	private CheckBox taskOverdueBannerCheck;
	@FXML
	private CheckBox taskDueTodayBannerCheck;
	@FXML
	private CheckBox appUpdatesBannerCheck;
	@FXML
	private CheckBox connectivityBannerCheck;
	@FXML
	private Label notificationSettingsStatusLabel;

	private NotificationPreferencesService notificationPreferencesService;
	private boolean fxmlReady;

	@FXML
	private void initialize() {
		fxmlReady = true;
		if (notificationPreferencesService != null) {
			loadFromPreferences();
		}
	}

	public void init(NotificationPreferencesService notificationPreferencesService) {
		this.notificationPreferencesService = Objects.requireNonNull(notificationPreferencesService, "notificationPreferencesService");
		if (fxmlReady) {
			loadFromPreferences();
		}
	}

	@FXML
	private void onApplyNotificationPreferences() {
		if (notificationPreferencesService == null) {
			return;
		}
		NotificationPreferences preferences = notificationPreferencesService.getForCurrentUser();
		Map<NotificationPreferenceKey, Boolean> selected = selectedValues();
		for (Map.Entry<NotificationPreferenceKey, Boolean> entry : selected.entrySet()) {
			preferences = preferences.withEnabled(entry.getKey(), entry.getValue());
		}
		notificationPreferencesService.setForCurrentUser(preferences);
		if (notificationSettingsStatusLabel != null) {
			notificationSettingsStatusLabel.setText("Notification settings applied for this session.");
		}
	}

	@FXML
	private void onResetNotificationPreferences() {
		loadFromPreferences();
		if (notificationSettingsStatusLabel != null) {
			notificationSettingsStatusLabel.setText("Notification settings reset to saved values.");
		}
	}

	private void loadFromPreferences() {
		if (notificationPreferencesService == null) {
			return;
		}
		NotificationPreferences preferences = notificationPreferencesService.getForCurrentUser();
		setChecked(taskAssignedToMeCheck, preferences.isEnabled(NotificationPreferenceKey.TASK_ASSIGNED_TO_ME));
		setChecked(taskOverdueCheck, preferences.isEnabled(NotificationPreferenceKey.TASK_DUE_OVERDUE));
		setChecked(taskDueTodayCheck, preferences.isEnabled(NotificationPreferenceKey.TASK_DUE_TODAY));
		setChecked(taskDueTomorrowCheck, preferences.isEnabled(NotificationPreferenceKey.TASK_DUE_TOMORROW));
		setChecked(appUpdatesCheck, preferences.isEnabled(NotificationPreferenceKey.APP_UPDATES));
		setChecked(connectivityCheck, preferences.isEnabled(NotificationPreferenceKey.CONNECTIVITY_STATUS));
		setChecked(taskOverdueBannerCheck, preferences.isEnabled(NotificationPreferenceKey.TASK_DUE_OVERDUE_BANNER));
		setChecked(taskDueTodayBannerCheck, preferences.isEnabled(NotificationPreferenceKey.TASK_DUE_TODAY_BANNER));
		setChecked(appUpdatesBannerCheck, preferences.isEnabled(NotificationPreferenceKey.APP_UPDATES_BANNER));
		setChecked(connectivityBannerCheck, preferences.isEnabled(NotificationPreferenceKey.CONNECTIVITY_BANNER));
		if (notificationSettingsStatusLabel != null) {
			notificationSettingsStatusLabel.setText("");
		}
	}

	private Map<NotificationPreferenceKey, Boolean> selectedValues() {
		Map<NotificationPreferenceKey, Boolean> values = new EnumMap<>(NotificationPreferenceKey.class);
		values.put(NotificationPreferenceKey.TASK_ASSIGNED_TO_ME, isChecked(taskAssignedToMeCheck));
		values.put(NotificationPreferenceKey.TASK_DUE_OVERDUE, isChecked(taskOverdueCheck));
		values.put(NotificationPreferenceKey.TASK_DUE_TODAY, isChecked(taskDueTodayCheck));
		values.put(NotificationPreferenceKey.TASK_DUE_TOMORROW, isChecked(taskDueTomorrowCheck));
		values.put(NotificationPreferenceKey.APP_UPDATES, isChecked(appUpdatesCheck));
		values.put(NotificationPreferenceKey.CONNECTIVITY_STATUS, isChecked(connectivityCheck));
		values.put(NotificationPreferenceKey.TASK_DUE_OVERDUE_BANNER, isChecked(taskOverdueBannerCheck));
		values.put(NotificationPreferenceKey.TASK_DUE_TODAY_BANNER, isChecked(taskDueTodayBannerCheck));
		values.put(NotificationPreferenceKey.APP_UPDATES_BANNER, isChecked(appUpdatesBannerCheck));
		values.put(NotificationPreferenceKey.CONNECTIVITY_BANNER, isChecked(connectivityBannerCheck));
		return values;
	}

	private static boolean isChecked(CheckBox checkBox) {
		return checkBox != null && checkBox.isSelected();
	}

	private static void setChecked(CheckBox checkBox, boolean selected) {
		if (checkBox != null) {
			checkBox.setSelected(selected);
		}
	}
}
