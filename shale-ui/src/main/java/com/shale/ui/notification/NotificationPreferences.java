package com.shale.ui.notification;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

public final class NotificationPreferences {
	private final EnumMap<NotificationPreferenceKey, Boolean> enabledByKey;

	private NotificationPreferences(EnumMap<NotificationPreferenceKey, Boolean> enabledByKey) {
		this.enabledByKey = enabledByKey;
	}

	public static NotificationPreferences defaults() {
		EnumMap<NotificationPreferenceKey, Boolean> map = new EnumMap<>(NotificationPreferenceKey.class);
		for (NotificationPreferenceKey key : NotificationPreferenceKey.values()) {
			map.put(key, Boolean.TRUE);
		}
		return new NotificationPreferences(map);
	}

	public boolean isEnabled(NotificationPreferenceKey key) {
		Objects.requireNonNull(key, "key");
		return enabledByKey.getOrDefault(key, Boolean.TRUE);
	}

	public NotificationPreferences withEnabled(NotificationPreferenceKey key, boolean enabled) {
		Objects.requireNonNull(key, "key");
		EnumMap<NotificationPreferenceKey, Boolean> copy = new EnumMap<>(enabledByKey);
		copy.put(key, enabled);
		return new NotificationPreferences(copy);
	}

	public Map<NotificationPreferenceKey, Boolean> asMap() {
		return Collections.unmodifiableMap(enabledByKey);
	}
}
