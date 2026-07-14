package com.shale.data.dao;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

final class TaskDaoCompletionStatusSynchronizationTest {
    private static String source() throws Exception {
        return Files.readString(Path.of("src/main/java/com/shale/data/dao/TaskDao.java"));
    }

    @Test
    void completionWritesSynchronizeTaskStatusIdToConfiguredCompletedStatus() throws Exception {
        String source = source();
        assertTrue(source.contains("TASK_STATUS_SYSTEM_KEY_COMPLETED = \"completed\""));
        assertTrue(source.contains("private static int resolveCompletedTaskStatusId"));
        assertTrue(source.contains("StatusId = ?"));
        assertTrue(source.contains("completed\n                    ? resolveCompletedTaskStatusId(con, shaleClientId, null)"));
        assertTrue(source.contains("if (completed) {\n                resolvedStatusId = resolveCompletedTaskStatusId(con, shaleClientId, resolvedStatusId);\n            }"));
    }
}
