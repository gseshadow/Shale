package com.shale.ui.controller;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class TaskCardSharedUsageRegressionTest {
    @Test
    void knownTaskScreensUseSharedTaskCardFactory() throws Exception {
        for (String file : java.util.List.of(
                "src/main/java/com/shale/ui/controller/MyShaleController.java",
                "src/main/java/com/shale/ui/controller/CaseController.java",
                "src/main/java/com/shale/ui/controller/UserController.java",
                "src/main/java/com/shale/ui/controller/SearchController.java",
                "src/main/java/com/shale/ui/controller/CalendarController.java")) {
            String source = Files.readString(Path.of(file));
            assertTrue(source.contains("TaskCardFactory"), file + " should route task cards through the shared factory.");
            assertFalse(source.contains("new TaskCard()"), file + " must not construct standalone task cards.");
            assertFalse(source.contains("setBorderByDueState"), file + " must not duplicate due-date accent rules.");
        }
    }
}
