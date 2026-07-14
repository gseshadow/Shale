package com.shale.data.dao;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

final class TaskDaoActiveTaskStatusRegressionTest {
    @Test
    void myTaskActiveQueriesDoNotHardcodeCompletedStatusId() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/shale/data/dao/TaskDao.java"));
        String assignedQuery = source.substring(source.indexOf("public List<CaseTaskListItemDto> listActiveTasksAssignedToUser"), source.indexOf("public List<CaseTaskListItemDto> listTasksCreatedByUserForBoard"));
        String createdQuery = source.substring(source.indexOf("public List<CaseTaskListItemDto> listTasksCreatedByUserForBoard"), source.indexOf("public List<AssignedUserTaskRow> listActiveTasksForAssigneeInTenant"));
        assertFalse(assignedQuery.contains("StatusId, 0) <> 3"));
        assertFalse(createdQuery.contains("StatusId, 0) <> 3"));
        assertTrue(assignedQuery.contains("AND ISNULL(t.IsDeleted, 0) = 0"));
        assertTrue(createdQuery.contains("AND ISNULL(t.IsDeleted, 0) = 0"));
        assertTrue(assignedQuery.contains("AND t.CompletedAt IS NULL"));
        assertTrue(createdQuery.contains("AND t.CompletedAt IS NULL"));
    }
}
