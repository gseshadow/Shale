package com.shale.data.dao;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class NotificationPhase0FoundationContractTest {
    @Test void migrationIsIdempotentDataPreservingAndStrictTenantScoped() throws Exception {
        String sql=Files.readString(Path.of("../docs/sql/2026-07-27_notifications_phase0_foundation.sql"));
        assertTrue(sql.contains("IF COL_LENGTH(N'dbo.Notifications', N'ExpiresAt') IS NULL"));
        assertTrue(sql.contains("IF COL_LENGTH(N'dbo.Notifications', N'RowVer') IS NULL"));
        assertTrue(sql.contains("HAVING COUNT_BIG(*) > 1"));
        assertTrue(sql.contains("CREATE UNIQUE INDEX UX_Notifications_Tenant_User_EventKey"));
        assertTrue(sql.contains("sec.fn_FilterByTenant(ShaleClientId) ON dbo.Notifications"));
        assertFalse(sql.toUpperCase().contains("DROP TABLE"));
        assertFalse(sql.contains("DetailedTitle"));
        assertFalse(sql.contains("NotificationPresentations"));
    }

    @Test void daoCursorAndMutationsRemainTenantAndRecipientScoped() throws Exception {
        String source=Files.readString(Path.of("src/main/java/com/shale/data/dao/NotificationDao.java"));
        assertTrue(source.contains("n.ShaleClientId=? AND n.UserId=? AND n.Id>?"));
        assertTrue(source.contains("ORDER BY n.Id ASC"));
        assertTrue(source.contains("n.ExpiresAt IS NULL OR n.ExpiresAt>SYSUTCDATETIME()"));
        assertTrue(source.contains("WHERE Id = ?\n\t\t\t\t  AND ShaleClientId = ?\n\t\t\t\t  AND UserId = ?"));
        assertFalse(source.contains("public void markNotificationRead(long notificationId)"));
        assertFalse(source.contains("public void markNotificationDismissed(long notificationId)"));
        assertFalse(source.contains("public void markNotificationsRead(List<Long>"));
        assertFalse(source.contains("public void markNotificationsDismissed(List<Long>"));
    }
}
