package com.shale.ui.services;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.shale.core.service.UserServicePort.FirmWideRoleAssignment;
import com.shale.core.service.UserServicePort.FirmWideRoleDefinition;
import com.shale.data.dao.UserDao.UserDetailRow;

final class UserDetailFirmWideRolesTest {
    private static final byte[] VERSION = {1};
    private static UserDetailRow user(boolean admin, boolean attorney) {
        return new UserDetailRow(25, 7, "Amy", "Jaramillo", "Amy Jaramillo", "amy@example.test", "", "", "AJ", admin, attorney, false, VERSION);
    }
    private static FirmWideRoleDefinition role(int id, String key, String name) {
        return new FirmWideRoleDefinition(id, 7, key, name, true, false, VERSION);
    }

    @Test void displaysAuthoritativeBuiltInsAndOnlyActiveCustomAssignments() {
        var admin = role(10, "ADMIN", "Administrator");
        var attorney = role(11, "ATTORNEY", "Attorney");
        var intake = role(12, "CUSTOM_INTAKE", "Intake");
        var history = List.of(new FirmWideRoleAssignment(100, 25, 12, "Intake", false, VERSION));
        var result = UserDetailService.composeRoleSnapshot(user(true, false), List.of(admin, attorney, intake), history, false);
        assertEquals(List.of("Administrator", "Intake"), result.assigned().stream().map(UserDetailService.RoleMembership::name).toList(),
                "User View must combine Users flags with active firm-wide assignments.");
        assertTrue(result.available().isEmpty(), "Ordinary viewers must not receive mutation choices.");
    }

    @Test void filtersAssignedRolesAndCarriesRemovedAssignmentForRestore() {
        var admin = role(10, "ADMIN", "Administrator");
        var intake = role(12, "CUSTOM_INTAKE", "Intake");
        var removed = new FirmWideRoleAssignment(100, 25, 12, "Intake", true, VERSION);
        var result = UserDetailService.composeRoleSnapshot(user(false, false), List.of(admin, intake), List.of(removed), true);
        assertTrue(result.assigned().isEmpty());
        assertEquals(List.of("Administrator", "Intake"), result.available().stream().map(UserDetailService.RoleMembership::name).toList());
        assertNull(result.available().get(0).assignment(), "An unassigned built-in has no assignment row.");
        assertEquals(100, result.available().get(1).assignment().id(), "A removed custom assignment must be retained for restore.");
    }
}
