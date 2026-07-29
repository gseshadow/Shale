package com.shale.ui.controller;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class MyShaleProgressiveStartupRegressionTest {
    private static final Path CONTROLLER = Path.of("src/main/java/com/shale/ui/controller/MyShaleController.java");

    @Test void independentLoadsUseBackgroundExecutorsAndProgressiveFxApplications() throws Exception {
        String source = Files.readString(CONTROLLER);
        assertTrue(source.contains("newFixedThreadPool(3"));
        assertTrue(source.indexOf("submitTaskMetadataLoad(\"priorities\"") < source.indexOf("tasksDbExec.submit"));
        assertTrue(source.contains("runOnFx(() -> applyTasks"));
        assertTrue(source.contains("runOnFx(() -> applyAssignedUsers"));
        assertFalse(source.contains(".join()"));
    }

    @Test void taskContentDoesNotWaitForMetadataAndStaleContextsAreRejected() throws Exception {
        String source = Files.readString(CONTROLLER);
        int applyTasks = source.indexOf("private void applyTasks");
        int applyUsers = source.indexOf("private void applyAssignedUsers");
        assertTrue(applyTasks > 0 && applyTasks < applyUsers);
        assertTrue(source.contains("Objects.equals(appState.getShaleClientId(), tenant)"));
        assertTrue(source.contains("Objects.equals(appState.getUserId(), user)"));
        assertTrue(source.contains("A metadata failure is deliberately section-local"));
    }

    @Test void lookupCacheIsBoundedAndTenantScoped() throws Exception {
        String source = Files.readString(CONTROLLER);
        assertTrue(source.contains("MY_SHALE_PRIORITY_CACHE_TTL_NANOS"));
        assertTrue(source.contains("Objects.equals(cachedPriorityTenantId, shaleClientId)"));
        assertTrue(source.contains("cachedPriorityTenantId = null"));
    }
}
