package com.shale.data.dao;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

final class TaskDaoCompletionStatusSynchronizationTest {
    private static String source() throws Exception {
        return Files.readString(Path.of("src/main/java/com/shale/data/dao/TaskDao.java"));
    }

    @Test
    void completionWritesSynchronizeTaskStatusIdToConfiguredCompletedStatus() throws Exception {
        String source = source();
        String completionMethod = methodBody(source, "private void updateTaskCompletion");
        String updateMethod = methodBody(source, "public void updateTask");

        assertTrue(source.contains("TASK_STATUS_SYSTEM_KEY_COMPLETED = \"completed\""));
        assertTrue(source.contains("private static int resolveCompletedTaskStatusId"));
        assertTrue(completionMethod.contains("StatusId = ?"));
        assertTrue(matches(completionMethod,
                "int\\s+\\w*StatusId\\s*=\\s*completed\\s*\\?\\s*resolveCompletedTaskStatusId\\(con,\\s*shaleClientId,\\s*null\\)\\s*:\\s*resolveDefaultTaskStatusId\\(con,\\s*shaleClientId\\)"),
                "Task completion must resolve StatusId through configured task status rows instead of hardcoded ids.");
        assertTrue(matches(completionMethod, "ps\\.setInt\\(1,\\s*\\w*StatusId\\)"),
                "Task completion must bind the resolved status id to the StatusId SQL parameter.");
        assertTrue(matches(updateMethod,
                "if\\s*\\(completed\\)\\s*\\{\\s*resolvedStatusId\\s*=\\s*resolveCompletedTaskStatusId\\(con,\\s*shaleClientId,\\s*resolvedStatusId\\);\\s*}"),
                "Task detail updates that mark completion must also synchronize StatusId through the configured completed status.");
    }

    private static String methodBody(String source, String methodSignaturePrefix) {
        int methodIndex = source.indexOf(methodSignaturePrefix + "(");
        assertTrue(methodIndex >= 0, "Expected TaskDao to contain method " + methodSignaturePrefix);
        int bodyStart = source.indexOf('{', methodIndex);
        assertTrue(bodyStart >= 0, "Expected method " + methodSignaturePrefix + " to have a body");
        int depth = 0;
        for (int i = bodyStart; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return source.substring(bodyStart, i + 1);
                }
            }
        }
        throw new AssertionError("Expected method " + methodSignaturePrefix + " body to close");
    }

    private static boolean matches(String source, String regex) {
        return Pattern.compile(regex, Pattern.DOTALL).matcher(source).find();
    }
}
