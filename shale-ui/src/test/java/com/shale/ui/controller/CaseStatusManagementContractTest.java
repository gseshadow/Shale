package com.shale.ui.controller;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

/** Architectural contracts whose regressions would recreate the eager Settings editor or split launch paths. */
class CaseStatusManagementContractTest {
    @Test
    void settingsIsCompactAndDoesNotEagerlyLoadStatuses() throws Exception {
        String fxml = Files.readString(Path.of("src/main/resources/fxml/settings.fxml"));
        String controller = Files.readString(Path.of("src/main/java/com/shale/ui/controller/SettingsController.java"));
        assertTrue(fxml.contains("Manage case statuses, colors, workflow state, and order."));
        assertTrue(fxml.contains("fx:id=\"manageCaseStatusesButton\""));
        assertFalse(fxml.contains("caseStatusCardsContainer"));
        assertFalse(controller.contains("loadCaseStatusesAsync"), "Settings must not own or eagerly load the manager");
        assertTrue(controller.contains("new CaseStatusManagementLauncher(caseService, settingsLoadExecutor)"));
    }

    @Test
    void caseContextUsesSameLauncherAndGuardsDirtyNavigation() throws Exception {
        String fxml = Files.readString(Path.of("src/main/resources/fxml/case.fxml"));
        String controller = Files.readString(Path.of("src/main/java/com/shale/ui/controller/CaseController.java"));
        assertTrue(fxml.contains("fx:id=\"manageCaseStatusesButton\""));
        assertTrue(controller.contains("new CaseStatusManagementLauncher(caseService, caseDateExecutor)"));
        assertTrue(controller.contains("if (editMode || detailsEditMode)"));
        assertTrue(controller.contains("Save or cancel the current Case edits before managing Case Statuses."));
        assertTrue(controller.contains("documentGeneration != openingNavigationGeneration"));
        assertTrue(controller.contains("statusesByTenantCache.remove(openingTenantId)"));
        assertTrue(controller.contains("if (!result.changed()"));
    }

    @Test
    void managerPreservesLegacyCommandsAndThreadingBoundaries() throws Exception {
        String pane = Files.readString(Path.of("src/main/java/com/shale/ui/controller/CaseStatusManagementPane.java"));
        assertTrue(pane.contains("service.listCaseStatuses(tenantId, true)"));
        assertTrue(pane.contains("service.listTenantCaseStatuses(tenantId, true)"));
        assertTrue(pane.contains("existing == null ? null : existing.lifecycleKey()"));
        assertTrue(pane.contains("existing == null ? null : existing.systemKey()"));
        assertTrue(pane.contains("service.reorderCaseStatuses(tenantId, first, second)"));
        assertTrue(pane.contains("service.removeCaseStatus(command)"));
        assertTrue(pane.contains("service.restoreCaseStatus(command)"));
        assertTrue(pane.contains("requireWorkerThread(); operation.run()"));
        assertTrue(pane.contains("changes.markCommitted(); load()"));
        assertFalse(pane.contains("shutdown"), "the externally owned executor must never be shut down");
        assertFalse(pane.contains("RowVer"), "the legacy status contract must not fabricate row-version concurrency");
    }
}
