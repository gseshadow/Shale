package com.shale.ui.controller;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

final class PracticeAreaManagementMigrationTest {
    @Test
    void settingsUsesCompactLazySharedLauncher() throws Exception {
        String fxml = Files.readString(Path.of("src/main/resources/fxml/settings.fxml"));
        String controller = Files.readString(Path.of("src/main/java/com/shale/ui/controller/SettingsController.java"));
        assertTrue(fxml.contains("Manage practice-area names, colors, and availability."));
        assertTrue(fxml.contains("fx:id=\"managePracticeAreasButton\""));
        assertFalse(fxml.contains("practiceAreaCardsContainer"));
        assertTrue(controller.contains("new PracticeAreaManagementLauncher"));
        assertFalse(controller.contains("new PracticeAreaManagementPane"));
        assertFalse(controller.contains("loadPracticeAreasAsync"), "Settings must not eagerly load Practice Areas");
    }

    @Test
    void caseAndSettingsUseSameLauncherWithDirtyAndNavigationGuards() throws Exception {
        String settings = Files.readString(Path.of("src/main/java/com/shale/ui/controller/SettingsController.java"));
        String cases = Files.readString(Path.of("src/main/java/com/shale/ui/controller/CaseController.java"));
        assertTrue(settings.contains("PracticeAreaManagementLauncher"));
        assertTrue(cases.contains("PracticeAreaManagementLauncher"));
        assertTrue(cases.contains("editMode || detailsEditMode"));
        assertTrue(cases.contains("Save or cancel the current Case edits"));
        assertTrue(cases.contains("!result.changed()"));
        assertTrue(cases.contains("caseId != openingCaseId"));
        assertTrue(cases.contains("practiceAreasByTenantCache.remove(openingTenantId)"));
    }

    @Test
    void paneExposesOnlyLegacyLifecycleAndRunsServiceWorkOffFxThread() throws Exception {
        String pane = Files.readString(Path.of("src/main/java/com/shale/ui/controller/PracticeAreaManagementPane.java"));
        assertTrue(pane.contains("listPracticeAreas(tenantId, true)"));
        assertTrue(pane.contains("listTenantPracticeAreas(tenantId, true)"));
        assertTrue(pane.contains("createPracticeArea(command)"));
        assertTrue(pane.contains("updatePracticeArea(command)"));
        assertTrue(pane.contains("deactivatePracticeArea(tenantId, snapshotId)"));
        assertTrue(pane.contains("requireWorkerThread()"));
        assertTrue(pane.contains("changes.markCommitted()"));
        assertTrue(pane.contains("row.shaleClientId() != null && row.active()"));
        assertTrue(pane.contains("Button deactivate = button(\"Deactivate\""));
        assertFalse(pane.contains("RowVer"));
        assertFalse(pane.contains("Restore"));
        assertFalse(pane.contains("Reset"));
        assertFalse(pane.contains("Reorder"));
        assertFalse(pane.contains("removePracticeArea"));
    }
}
