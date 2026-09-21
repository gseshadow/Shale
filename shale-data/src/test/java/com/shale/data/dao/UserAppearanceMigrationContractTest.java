package com.shale.data.dao;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

final class UserAppearanceMigrationContractTest {
    @Test
    void migrationIsTransactionalRerunnableTenantProtectedAndSelfVerifying() throws Exception {
        String sql = Files.readString(Path.of("../docs/sql/2026-09-21_user_appearance_preference_phase7.sql"));
        assertTrue(sql.contains("SET XACT_ABORT ON"));
        assertTrue(sql.contains("BEGIN TRY") && sql.contains("BEGIN TRANSACTION") && sql.contains("ROLLBACK TRANSACTION"));
        assertTrue(sql.contains("CK_UserPreferences_AppearanceTheme"));
        assertTrue(sql.contains("sec.fn_FilterByTenant(ShaleClientId) ON dbo.UserPreferences"));
        assertTrue(sql.contains("Invalid appearance value") && sql.contains("Orphan or cross-tenant user")
                && sql.contains("Duplicate tenant/user/key"));
    }
}
