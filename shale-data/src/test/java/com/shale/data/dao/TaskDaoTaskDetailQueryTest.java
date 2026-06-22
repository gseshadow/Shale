package com.shale.data.dao;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

final class TaskDaoTaskDetailQueryTest {

    @Test
    void taskDetailUsesDirectCreatedByUserJoinForDisplayName() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/shale/data/dao/TaskDao.java"));
        String method = source.substring(
                source.indexOf("public TaskDetailDto findTaskDetail"),
                source.indexOf("public List<TaskAssignedUserRow> listAssignedUsersForTask"));

        assertTrue(method.contains("LEFT JOIN dbo.Users createdByUser"));
        assertTrue(method.contains("createdByUser.name_first"));
        assertTrue(method.contains("AS CreatedByDisplayName"));
        assertFalse(method.contains(") creator"),
                "Task detail must not use the prior creator OUTER APPLY alias for created-by display names");
    }

    @Test
    void taskDetailHydrationUsesSafeNullableTypeReaders() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/shale/data/dao/TaskDao.java"));
        String method = source.substring(
                source.indexOf("public TaskDetailDto findTaskDetail"),
                source.indexOf("public List<TaskAssignedUserRow> listAssignedUsersForTask"));

        assertTrue(method.contains("getNullableBoolean(rs, \"CaseNonEngagementLetterSent\")"));
        assertTrue(method.contains("getNullableInt(rs, \"StatusId\")"));
        assertTrue(method.contains("getNullableInt(rs, \"PriorityId\")"));
        assertTrue(method.contains("getNullableInt(rs, \"AssignedUserId\")"));
        assertFalse(method.contains("(Integer) rs.getObject"));
        assertFalse(method.contains("(Boolean) rs.getObject"));
    }
}
