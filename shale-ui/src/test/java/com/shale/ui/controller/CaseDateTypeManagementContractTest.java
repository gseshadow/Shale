package com.shale.ui.controller;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.shale.core.dto.EffectiveCaseDateTypeDto;

final class CaseDateTypeManagementContractTest {
    private static String read(String path) { try { return Files.readString(Path.of(path)); } catch (Exception ex) { throw new AssertionError(ex); } }
    private static final String PANE = read("src/main/java/com/shale/ui/controller/CaseDateTypeManagementPane.java");
    private static final String WINDOW = read("src/main/java/com/shale/ui/component/DefinitionManagementWindow.java");
    private static final String CASE = read("src/main/java/com/shale/ui/controller/CaseController.java");

    @Test void settingsAndCaseDatesShareOneLauncherAndPane() {
        String settings = read("src/main/java/com/shale/ui/controller/SettingsController.java");
        assertTrue(settings.contains("new CaseDateTypeManagementLauncher"));
        assertTrue(CASE.contains("new CaseDateTypeManagementLauncher"));
        assertTrue(read("src/main/java/com/shale/ui/controller/CaseDateTypeManagementLauncher.java").contains("new CaseDateTypeManagementPane"));
    }

    @Test void backgroundGuardsAndCompletionContractAreExplicit() {
        assertTrue(PANE.contains("executor.execute"));
        assertTrue(PANE.contains("Platform.runLater"));
        assertTrue(PANE.contains("generation == loadGeneration"));
        assertTrue(PANE.contains("mutationInFlight.compareAndSet(false, true)"));
        assertTrue(PANE.contains("changed.set(true)"));
        assertTrue(WINDOW.contains("if (changed.get()) onClosed.accept"));
        assertTrue(WINDOW.contains("dispose.run()"));
    }

    @Test void tenantDefinitionsHaveCorrectLifecycleActionsAndGlobalsAreNotManageable() {
        var global = type(1, null, "required", true);
        var active = type(2, 7, null, true);
        var inactive = type(3, 7, null, false);
        var otherTenant = type(4, 8, null, true);
        assertEquals(List.of(active, inactive), CaseDateTypeManagementPane.manageableRows(List.of(global, active, inactive, otherTenant), 7));
        assertFalse(CaseDateTypeManagementPane.isManageable(global, 7));
        assertTrue(CaseDateTypeManagementPane.isManageable(active, 7));
        assertTrue(CaseDateTypeManagementPane.isManageable(inactive, 7));
        assertEquals("Deactivate", CaseDateTypeManagementPane.lifecycleActionLabel(active));
        assertEquals("Activate", CaseDateTypeManagementPane.lifecycleActionLabel(inactive));
        assertTrue(PANE.contains("resetCaseDateTypeOverride"));
    }

    @Test void commandPreservesIdentityContextFieldsAndRowVersion() {
        byte[] rowVer = {1, 2};
        var input = new CaseDateTypeManagementPane.Input("Name", "Description", "DEADLINE", "#112233", true, 12, false);
        var command = CaseDateTypeManagementPane.command(9, input, "stable_key", rowVer, 7, 8);
        assertAll(() -> assertEquals(9, command.id()), () -> assertEquals(7, command.shaleClientId()),
                () -> assertEquals(8, command.actorUserId()), () -> assertEquals("stable_key", command.systemKey()),
                () -> assertEquals("Name", command.name()), () -> assertEquals("Description", command.description()),
                () -> assertEquals("DEADLINE", command.calendarCategory()), () -> assertEquals("#112233", command.color()),
                () -> assertTrue(command.supportsTime()), () -> assertEquals(12, command.sortOrder()),
                () -> assertFalse(command.active()), () -> assertArrayEquals(rowVer, command.expectedRowVer()));
    }

    @Test void caseActionIsAdminOnlyAndRefreshesAllDependentReadModelsWithoutOccurrenceMutation() {
        assertTrue(CASE.contains("manageCaseDateTypesButton.setVisible(appState != null && appState.isAdmin())"));
        assertTrue(CASE.contains("if (!result.changed()"));
        assertTrue(CASE.contains("loadCaseDatesAsync()"));
        assertTrue(CASE.contains("loadOverviewConfigurationAsync()"));
        assertTrue(CASE.contains("loadCompatibilityDatesAsync(openingCaseId)"));
        String method = CASE.substring(CASE.indexOf("private void openCaseDateTypeManagement"), CASE.indexOf("private void resetCaseDatesState"));
        assertFalse(method.contains("createCaseDate(")); assertFalse(method.contains("updateCaseDate(")); assertFalse(method.contains("deleteCaseDate("));
        assertTrue(method.contains("caseId != openingCaseId"));
    }

    private static EffectiveCaseDateTypeDto type(int id, Integer tenant, String key, boolean active) {
        return new EffectiveCaseDateTypeDto(id, tenant, key, "Type " + id, null, "OTHER", "#112233", true, id, active, false,
                tenant == null ? EffectiveCaseDateTypeDto.Origin.GLOBAL : EffectiveCaseDateTypeDto.Origin.TENANT_CREATED, new byte[]{1});
    }
}
