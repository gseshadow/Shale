package com.shale.ui.controller;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

final class FirmWideRoleManagementContractTest {
    private static final Path PANE = Path.of("src/main/java/com/shale/ui/controller/FirmWideRoleAdminPane.java");

    @Test void protectedBuiltInsHaveExplanationAndNoMutationActions() throws Exception {
        String source = Files.readString(PANE);
        assertAll(
                () -> assertTrue(source.contains("Administrator and Attorney are protected built-ins")),
                () -> assertTrue(source.contains("definition.builtIn()")),
                () -> assertTrue(source.contains("Managed by user flags")),
                () -> assertTrue(source.contains("else if (!definition.deleted())")));
    }

    @Test void lifecycleUsesServicePortRowVersionsAndPreservesAssignmentHistoryMessaging() throws Exception {
        String source = Files.readString(PANE);
        assertAll(
                () -> assertTrue(source.contains("CreateFirmWideRoleCommand")),
                () -> assertTrue(source.contains("RenameFirmWideRoleCommand")),
                () -> assertTrue(source.contains("FirmWideRoleLifecycleCommand")),
                () -> assertTrue(source.contains("definition.rowVer()")),
                () -> assertTrue(source.contains("assignment history will be preserved")),
                () -> assertFalse(source.contains("UserDao")),
                () -> assertFalse(source.matches("(?s).*\\b(INSERT|UPDATE|DELETE)\\s+dbo\\..*")));
    }

    @Test void loadHasEmptyErrorAndStaleResultContracts() throws Exception {
        String source = Files.readString(PANE);
        assertAll(
                () -> assertTrue(source.contains("request != generation")),
                () -> assertTrue(source.contains("No role definitions are available.")),
                () -> assertTrue(source.contains("Select Refresh to try again.")),
                () -> assertTrue(source.contains("changes.markCommitted()")));
    }
}
