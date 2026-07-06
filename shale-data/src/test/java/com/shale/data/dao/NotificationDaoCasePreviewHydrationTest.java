package com.shale.data.dao;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

final class NotificationDaoCasePreviewHydrationTest {

    @Test
    void unreadNotificationRowsHydrateCasePreviewVisualFieldsLikeTaskCards() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/shale/data/dao/NotificationDao.java"));
        String method = source.substring(
                source.indexOf("public List<NotificationRow> listUnreadNotificationsForUser"),
                source.indexOf("public void markNotificationRead"));

        assertTrue(method.contains("current_status.CurrentStatusName") && method.contains("AS CasePrimaryStatusName"),
                "Notifications should hydrate the case primary status name used by embedded case cards.");
        assertTrue(method.contains("current_status.PrimaryStatusColor") && method.contains("AS CasePrimaryStatusColor"),
                "Notifications should hydrate the case primary status color used by embedded case cards.");
        assertTrue(method.contains("pa.Color") && method.contains("AS CasePracticeAreaColor"),
                "Notifications should hydrate the practice-area color used by embedded case cards.");
        assertTrue(method.contains("LEFT JOIN dbo.PracticeAreas pa"),
                "Notifications should use the same case practice-area join pattern as task card hydration.");
        assertTrue(method.contains("OUTER APPLY (")
                        && method.contains("FROM dbo.CaseStatuses cs")
                        && method.contains("INNER JOIN dbo.Statuses s ON s.Id = cs.StatusId")
                        && method.contains("cs.CaseId = c.Id AND cs.IsPrimary = 1"),
                "Notifications should use the same primary-status OUTER APPLY pattern as task card hydration.");
        assertTrue(method.contains("rs.getString(\"CasePrimaryStatusName\")")
                        && method.contains("rs.getString(\"CasePrimaryStatusColor\")")
                        && method.contains("rs.getString(\"CasePracticeAreaColor\")"),
                "NotificationRow mapping should expose the hydrated visual fields.");
    }
}
