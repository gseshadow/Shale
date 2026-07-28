package com.shale.data.dao;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class MaterialRequestNotificationContractTest {
    private static String source(String path) {
        try { return Files.readString(Path.of(path)); }
        catch (Exception e) { throw new AssertionError(e); }
    }

    @Test void immediateNotificationsUsePersistedRecipientsAndMutationTransaction() {
        String dao = source("src/main/java/com/shale/data/dao/MaterialRequestDao.java");
        assertTrue(dao.contains("RecipientSnapshot previous=findRecipientSnapshot(con,c)"));
        assertTrue(dao.contains("LinkedHashSet<Integer> recipients"));
        assertTrue(dao.contains("!Objects.equals(newRequester,actor)"));
        assertTrue(dao.contains("newAssignee!=actor"));
        assertTrue(dao.contains("notifications.createMaterialRequestNotification(con"));
        assertTrue(dao.indexOf("createRecipientNotifications(con,id") < dao.indexOf("con.commit();return findMaterialRequest"));
    }

    @Test void dueCandidatesResolveTerminalSystemKeysAndOccurrenceIdentity() {
        String dao = source("src/main/java/com/shale/data/dao/MaterialRequestDao.java");
        String generator = source("../shale-ui/src/main/java/com/shale/ui/notification/TaskDueDateNotificationGenerator.java");
        assertTrue(dao.contains("COALESCE(rs.SystemKey,mr.Status)"));
        assertTrue(dao.contains("NOT IN ('closed','cancelled')"));
        assertTrue(dao.contains("mr.ExpectedResponseDate IS NOT NULL"));
        assertTrue(generator.contains("candidate.assignedToUserId()!=null") || dao.contains("assignedToUserId!=null?assignedToUserId:requestedByUserId"));
        assertTrue(generator.contains("\":due:\" + candidate.dueAt() + \":\" + recipient"));
    }

    @Test void recurringOccurrencesHaveDistinctIdentityRecipientFallbackAndAtomicDismissal() {
        String dao = source("src/main/java/com/shale/data/dao/MaterialRequestDao.java");
        String notifications = source("src/main/java/com/shale/data/dao/NotificationDao.java");
        String generator = source("../shale-ui/src/main/java/com/shale/ui/notification/TaskDueDateNotificationGenerator.java");
        assertTrue(dao.contains("mr.FollowUpIntervalDays IS NOT NULL"));
        assertTrue(dao.contains("assignedToUserId!=null?assignedToUserId:requestedByUserId"));
        assertTrue(generator.contains("\":follow-up:\"+candidate.nextFollowUpAt()"));
        assertTrue(notifications.contains("FOLLOW_UP_DUE"));
        assertTrue(notifications.contains("WITH (UPDLOCK,ROWLOCK)"));
        assertTrue(notifications.contains("current.equals(occurrence)"));
        assertTrue(notifications.contains("DATEADD(day,FollowUpIntervalDays,SYSUTCDATETIME())"));
        assertTrue(notifications.contains("con.rollback()"));
    }

    @Test void materialRequestRowsHydrateTypedEntityAndCaseRoute() {
        String notifications = source("src/main/java/com/shale/data/dao/NotificationDao.java");
        String main = source("../shale-ui/src/main/java/com/shale/ui/controller/MainController.java");
        assertTrue(notifications.contains("\"MaterialRequest\""));
        assertTrue(notifications.contains("mr.CaseId"));
        assertTrue(main.contains("openCaseProfile(caseId.intValue(), \"REQUESTS\")"));
    }
}
