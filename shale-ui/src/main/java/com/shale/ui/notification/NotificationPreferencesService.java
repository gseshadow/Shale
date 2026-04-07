package com.shale.ui.notification;

import com.shale.ui.state.AppState;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;

public final class NotificationPreferencesService {
	private final AppState appState;
	private final NotificationPreferences defaults = NotificationPreferences.defaults();
	private final Map<Integer, NotificationPreferences> perUserPreferences = new ConcurrentHashMap<>();
	private final ObjectProperty<NotificationPreferences> activePreferences = new SimpleObjectProperty<>(defaults);

	public NotificationPreferencesService(AppState appState) {
		this.appState = Objects.requireNonNull(appState, "appState");
	}

	public boolean isEnabled(NotificationPreferenceKey key) {
		return getForCurrentUser().isEnabled(key);
	}

	public NotificationPreferences getForCurrentUser() {
		Integer userId = appState.getUserId();
		if (userId == null || userId <= 0) {
			return defaults;
		}
		return perUserPreferences.computeIfAbsent(userId, ignored -> defaults);
	}

	public void setEnabledForCurrentUser(NotificationPreferenceKey key, boolean enabled) {
		Objects.requireNonNull(key, "key");
		Integer userId = appState.getUserId();
		if (userId == null || userId <= 0) {
			return;
		}
		NotificationPreferences updated = getForCurrentUser().withEnabled(key, enabled);
		perUserPreferences.put(userId, updated);
		activePreferences.set(updated);
	}

	public ObjectProperty<NotificationPreferences> activePreferencesProperty() {
		return activePreferences;
	}

	public void refreshActivePreferences() {
		activePreferences.set(getForCurrentUser());
	}
}
