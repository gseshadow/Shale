package com.shale.ui.services;

import com.shale.data.dao.UserPreferencesDao;
import com.shale.data.dao.UserPreferencesDao.PreferenceValue;
import com.shale.data.dao.UserPreferencesDao.UserPreferenceRow;
import com.shale.ui.state.AppState;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public final class UserPreferencesService {
	private static final String TYPE_BOOLEAN = "BOOLEAN";
	private static final String TYPE_STRING = "STRING";

	private final UserPreferencesDao userPreferencesDao;
	private final AppState appState;
	private final Map<Integer, Map<String, UserPreferenceRow>> cacheByUser = new ConcurrentHashMap<>();

	public UserPreferencesService(UserPreferencesDao userPreferencesDao, AppState appState) {
		this.userPreferencesDao = Objects.requireNonNull(userPreferencesDao, "userPreferencesDao");
		this.appState = Objects.requireNonNull(appState, "appState");
	}

	public boolean getBoolean(String key, boolean defaultValue) {
		UserPreferenceRow row = getRowForCurrentUser(key);
		if (row == null || row.preferenceValue() == null) {
			return defaultValue;
		}
		String normalized = row.preferenceValue().trim();
		if ("1".equals(normalized) || "true".equalsIgnoreCase(normalized)) {
			return true;
		}
		if ("0".equals(normalized) || "false".equalsIgnoreCase(normalized)) {
			return false;
		}
		return defaultValue;
	}

	public void putBoolean(String key, boolean value) {
		upsertForCurrentUser(key, value ? "true" : "false", TYPE_BOOLEAN);
	}

	public String getString(String key, String defaultValue) {
		UserPreferenceRow row = getRowForCurrentUser(key);
		if (row == null || row.preferenceValue() == null) {
			return defaultValue;
		}
		return row.preferenceValue();
	}

	public void putString(String key, String value) {
		upsertForCurrentUser(key, value, TYPE_STRING);
	}

	public Map<String, String> listStrings() {
		Map<String, UserPreferenceRow> all = loadAllForCurrentUser();
		return all.entrySet().stream()
				.collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().preferenceValue()));
	}

	public void refreshCurrentUser() {
		Integer userId = appState.getUserId();
		if (userId != null && userId > 0) {
			cacheByUser.remove(userId);
		}
	}

	private void upsertForCurrentUser(String key, String value, String type) {
		if (key == null || key.isBlank()) {
			return;
		}
		Integer userId = appState.getUserId();
		Integer shaleClientId = appState.getShaleClientId();
		if (userId == null || userId <= 0 || shaleClientId == null || shaleClientId <= 0) {
			return;
		}
		userPreferencesDao.upsertPreference(
				shaleClientId,
				userId,
				key,
				value,
				type,
				userId);
		cacheByUser.remove(userId);
	}

	private UserPreferenceRow getRowForCurrentUser(String key) {
		if (key == null || key.isBlank()) {
			return null;
		}
		return loadAllForCurrentUser().get(key);
	}

	private Map<String, UserPreferenceRow> loadAllForCurrentUser() {
		Integer userId = appState.getUserId();
		Integer shaleClientId = appState.getShaleClientId();
		if (userId == null || userId <= 0 || shaleClientId == null || shaleClientId <= 0) {
			return Map.of();
		}
		return cacheByUser.computeIfAbsent(userId, ignored ->
		{
			var list = userPreferencesDao.listPreferencesForUser(shaleClientId, userId);
			return list.stream()
					.filter(row -> row.preferenceKey() != null && !row.preferenceKey().isBlank())
					.collect(Collectors.toMap(UserPreferenceRow::preferenceKey, row -> row, (left, right) -> right));
		});
	}
}
