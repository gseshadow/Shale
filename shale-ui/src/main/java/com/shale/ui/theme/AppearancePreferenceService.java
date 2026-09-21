package com.shale.ui.theme;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import com.shale.ui.services.UserPreferencesService;

/** Typed authenticated-user boundary for the persisted Appearance preference. */
public final class AppearancePreferenceService {
    public static final String PREFERENCE_KEY = "appearance.theme";

    private final UserPreferencesService preferences;

    public AppearancePreferenceService(UserPreferencesService preferences) {
        this.preferences = Objects.requireNonNull(preferences, "preferences");
    }

    public Theme loadForCurrentUser() {
        return Theme.fromStoredValue(preferences.getString(PREFERENCE_KEY, Theme.LIGHT.storedValue()));
    }

    public void saveForCurrentUser(Theme theme) {
        preferences.putString(PREFERENCE_KEY, (theme == null ? Theme.LIGHT : theme).storedValue());
    }

    public CompletableFuture<Void> saveForCurrentUser(Theme theme, Executor executor) {
        Theme normalized = theme == null ? Theme.LIGHT : theme;
        return preferences.putStringAsync(PREFERENCE_KEY, normalized.storedValue(), executor);
    }

    public void clearSessionCache() {
        preferences.refreshCurrentUser();
    }
}
