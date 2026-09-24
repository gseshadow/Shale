package com.shale.ui.controller.support;

import static org.junit.jupiter.api.Assertions.*;

import com.shale.core.dto.EffectiveCaseDateTypeDto;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class NewIntakeDatesCustomizationDialogTest {
    @Test void tenantEffectiveProtectedIdsEnableSolAndTcnRegardlessOfSystemKey() {
        Set<Integer> protectedIds = Set.of(731, 944);
        assertTrue(NewIntakeDatesCustomizationDialog.supportsConfirmation(731, protectedIds));
        assertTrue(NewIntakeDatesCustomizationDialog.supportsConfirmation(944, protectedIds));
        assertFalse(NewIntakeDatesCustomizationDialog.supportsConfirmation(7, protectedIds));
    }

    @Test void requiredAndConfirmationHaveAllFourIndependentStates() {
        EffectiveCaseDateTypeDto type = type(731, "Tenant SOL", "tenant_deadline");
        for (boolean required : List.of(false, true)) {
            for (boolean confirmation : List.of(false, true)) {
                var selection = new NewIntakeDatesConfiguration.Selection(type, false);
                selection = NewIntakeDatesConfiguration.withRequired(selection, required);
                selection = NewIntakeDatesConfiguration.withConfirmation(selection, confirmation,
                        confirmation ? 42 : null);
                assertEquals(required, selection.required(), "Required must not gate confirmation");
                assertEquals(confirmation, selection.requiresConfirmation(), "Confirmation must not gate Required");
                assertEquals(confirmation ? 42 : null, selection.roleDefinitionId());
            }
        }
    }

    @Test void popupContractIsOwnerBoundStagedScrollableAndCloseDoesNotSave() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/shale/ui/controller/support/NewIntakeDatesCustomizationDialog.java"));
        assertAll(
                () -> assertTrue(source.contains("dialog.initOwner(owner)")),
                () -> assertTrue(source.contains("Modality.WINDOW_MODAL")),
                () -> assertTrue(source.contains("dialog.setResizable(true)")),
                () -> assertTrue(source.contains("new ScrollPane(rows)")),
                () -> assertTrue(source.contains("event -> dialog.close()")),
                () -> assertFalse(source.contains("saveHandler.accept(snapshot())")
                        && source.substring(source.indexOf("event -> dialog.close()") - 80,
                                source.indexOf("event -> dialog.close()") + 80).contains("saveHandler")));
    }

    @Test void roleSelectorUsesServiceRolesAndReportsLoadFailure() throws Exception {
        String dialog = Files.readString(Path.of("src/main/java/com/shale/ui/controller/support/NewIntakeDatesCustomizationDialog.java"));
        String controller = Files.readString(Path.of("src/main/java/com/shale/ui/controller/NewIntakeController.java"));
        assertAll(
                () -> assertTrue(controller.contains("caseService.listConfirmationRoles(tenant, actor)")),
                () -> assertTrue(controller.contains("java.util.Set.of(sol, tcn)")),
                () -> assertTrue(dialog.contains("role.getItems().setAll(roles)")),
                () -> assertTrue(dialog.contains("role.setDisable(!supported || !confirmation.isSelected() || !rolesAvailable)")),
                () -> assertTrue(dialog.contains("Confirming roles could not be loaded")),
                () -> assertTrue(dialog.contains("Reload configuration")));
    }

    @Test void saveReloadAndDraftValuePreservationUseStableIdentity() throws Exception {
        String controller = Files.readString(Path.of("src/main/java/com/shale/ui/controller/NewIntakeController.java"));
        assertAll(
                () -> assertTrue(controller.contains("formConfigurationService.replace(command)")),
                () -> assertTrue(controller.contains("caseService.setFieldConfirmationPolicy")),
                () -> assertTrue(controller.contains("loadDatesConfiguration()")),
                () -> assertTrue(controller.contains("preservedConfiguredDateValues.put(fieldKey, newValue)")),
                () -> assertTrue(controller.contains("NewIntakeDatesConfiguration.initialValue(fieldKey")),
                () -> assertTrue(controller.contains("Choose Reload configuration before saving again.")));
    }

    private static EffectiveCaseDateTypeDto type(int id, String name, String systemKey) {
        return new EffectiveCaseDateTypeDto(id, 7, systemKey, name, null, "OTHER", null, false, id,
                true, false, EffectiveCaseDateTypeDto.Origin.TENANT_OVERRIDE, new byte[]{1});
    }
}
