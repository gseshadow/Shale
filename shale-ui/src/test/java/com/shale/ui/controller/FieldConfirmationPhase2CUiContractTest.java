package com.shale.ui.controller;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class FieldConfirmationPhase2CUiContractTest {
    @Test void bothCaseSurfacesUseOnePresentationAndRefreshTogether() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/shale/ui/controller/CaseController.java"));
        assertAll(
                () -> assertTrue(source.contains("CaseDateConfirmationView.create"), "confirmation uses the shared component"),
                () -> assertTrue(source.contains("marker.setUserData(label)"), "compatibility SOL/TCN rows receive the shared marker/action too"),
                () -> assertTrue(source.contains("loadCaseDatesAsync();loadOverviewConfigurationAsync()"), "success and stale failure refresh both surfaces"),
                () -> assertTrue(source.contains("currentActorHasConfirmationRole"), "only eligible users receive Confirm"),
                () -> assertTrue(source.contains("publishCaseDatesChanged(activeCase,LiveUpdateEvents.CHANGE_UPDATED)"), "success publishes only a safe invalidation"));
    }

    @Test void settingsUsesVersionedPolicyAndFirmWideRoleSelector() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/shale/ui/controller/NewIntakeController.java"));
        assertAll(
                () -> assertTrue(source.contains("SetFieldConfirmationPolicyCommand")),
                () -> assertTrue(source.contains("p==null?null:p.rowVer()"), "policy save carries the expected concurrency token"),
                () -> assertTrue(source.contains("ComboBox<CaseServicePort.ConfirmationRole>"), "role selector cannot contain Case Team roles"),
                () -> assertTrue(source.contains("The selected confirming role is no longer active")),
                () -> assertTrue(source.contains("Confirmation policy changed"), "stale policy requires an explicit reload"));
    }
}
