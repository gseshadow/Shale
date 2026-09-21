package com.shale.data.dao;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

final class UserPreferencesAppearanceContractTest {
    @Test
    void persistenceBoundaryRejectsUnknownAppearanceValuesBeforeOpeningAConnection() {
        UserPreferencesDao dao = new UserPreferencesDao(() -> {
            throw new AssertionError("invalid values must be rejected before database access");
        });
        assertThrows(IllegalArgumentException.class,
                () -> dao.upsertPreference(7, 42, "appearance.theme", "SYSTEM", "STRING", 42));
        assertThrows(IllegalArgumentException.class,
                () -> dao.upsertPreference(7, 42, "appearance.theme", "DARK", "BOOLEAN", 42));
    }
}
