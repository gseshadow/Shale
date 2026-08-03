package com.shale.ui.controller;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

final class NewOrganizationSemanticControlsTest {
    @Test
    void fxmlControlsReceiveSemanticIdentityAfterInjection() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/shale/ui/controller/NewOrganizationController.java"));
        assertTrue(source.contains("ControlStyles.apply(cancelButton, ControlStyles.Purpose.SECONDARY)"));
        assertTrue(source.contains("ControlStyles.apply(createOrganizationButton, ControlStyles.Purpose.PRIMARY)"));
        assertTrue(source.contains("ControlStyles.formControl(organizationTypeComboBox)"));
        assertTrue(source.contains("ControlStyles.formControl(control)"));
    }
}
