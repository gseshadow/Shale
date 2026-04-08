package com.shale.ui.notification;

import com.shale.ui.state.AppState;
import com.shale.ui.services.UserPreferencesService;

import java.util.Objects;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;

public final class NotificationPreferencesService {
	private final AppState appState;
	private final UserPreferencesService userPreferencesService;
	private final NotificationPreferences defaults = NotificationPreferences.defaults();
	private final ObjectProperty<NotificationPreferences> activePreferences = new SimpleObjectProperty<>(defaults);

	public NotificationPreferencesService(AppState appState, UserPreferencesService userPreferencesService) {
		this.appState = Objects.requireNonNull(appState, "appState");
		this.userPreferencesService = Objects.requireNonNull(userPreferencesService, "userPreferencesService");
	}

	public boolean isEnabled(NotificationPreferenceKey key) {
		return getForCurrentUser().isEnabled(key);
	}

	public NotificationPreferences getForCurrentUser() {
		Integer userId = appState.getUserId();
		if (userId == null || userId <= 0) {
			return defaults;
		}
		NotificationPreferences current = defaults;
		for (NotificationPreferenceKey key : NotificationPreferenceKey.values()) {
			boolean enabled = userPreferencesService.getBoolean(storageKey(key), defaults.isEnabled(key));
			current = current.withEnabled(key, enabled);
		}
		return current;
	}

	public void setEnabledForCurrentUser(NotificationPreferenceKey key, boolean enabled) {
		Objects.requireNonNull(key, "key");
		userPreferencesService.putBoolean(storageKey(key), enabled);
		activePreferences.set(getForCurrentUser());
	}

	public void setForCurrentUser(NotificationPreferences preferences) {
		if (preferences == null) {
			return;
		}
		for (NotificationPreferenceKey key : NotificationPreferenceKey.values()) {
			userPreferencesService.putBoolean(storageKey(key), preferences.isEnabled(key));
		}
		activePreferences.set(getForCurrentUser());
	}

	public ObjectProperty<NotificationPreferences> activePreferencesProperty() {
		return activePreferences;
	}

	public void refreshActivePreferences() {
		userPreferencesService.refreshCurrentUser();
		activePreferences.set(getForCurrentUser());
	}

	private static String storageKey(NotificationPreferenceKey key) {
		return switch (key) {
			case TASK_ASSIGNED_TO_ME -> "notifications.task.assigned.enabled";
			case TASK_DUE_OVERDUE -> "notifications.task.overdue.enabled";
			case TASK_DUE_TODAY -> "notifications.task.due_today.enabled";
			case TASK_DUE_TOMORROW -> "notifications.task.due_tomorrow.enabled";
			case APP_UPDATES -> "notifications.app_updates.enabled";
			case CONNECTIVITY_STATUS -> "notifications.connectivity.enabled";
			case TASK_DUE_OVERDUE_BANNER -> "notifications.task.overdue.banner";
			case TASK_DUE_TODAY_BANNER -> "notifications.task.due_today.banner";
			case APP_UPDATES_BANNER -> "notifications.app_updates.banner";
			case CONNECTIVITY_BANNER -> "notifications.connectivity.banner";
			case CASE_ACTIVITY -> "notifications.case.activity.enabled";
		};
	}
}
