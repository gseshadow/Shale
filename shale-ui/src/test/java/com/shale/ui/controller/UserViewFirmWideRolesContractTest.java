package com.shale.ui.controller;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class UserViewFirmWideRolesContractTest {
    @Test void userViewUsesFirmWideServiceAndExplicitAsyncStates() throws Exception {
        String controller = Files.readString(Path.of("src/main/java/com/shale/ui/controller/UserController.java"));
        String service = Files.readString(Path.of("src/main/java/com/shale/ui/services/UserDetailService.java"));
        assertAll(
            () -> assertTrue(service.contains("listFirmWideRolesForUserView")),
            () -> assertTrue(service.contains("listUserFirmWideRoleAssignmentsForView")),
            () -> assertTrue(service.contains("restoreFirmWideRoleAssignment")),
            () -> assertTrue(service.contains("updateManagedUser"), "Built-ins must use protected audited User management."),
            () -> assertFalse(service.contains("listAssignableRoles"), "Legacy built-in-only role source must be retired from User View."),
            () -> assertTrue(controller.contains("RoleLoadState.LOADING")),
            () -> assertTrue(controller.contains("Roles could not be loaded.")),
            () -> assertTrue(controller.contains("target.shaleClientId()"), "Stale checks must include tenant identity."),
            () -> assertTrue(controller.indexOf("roleLoadState != RoleLoadState.LOADED") < controller.indexOf("No additional roles are available"))
        );
    }
}
