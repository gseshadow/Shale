package com.shale.core.service;

import java.util.List;
import java.util.Optional;

public interface UserServicePort {
    List<UserSummary> listTenantUsers(int shaleClientId);

    Optional<UserDetail> getUserDetail(int userId, int shaleClientId);

    /**
     * Checks the authenticated actor's current, active firm-wide membership against
     * authoritative database state. This operation is intentionally unrelated to
     * case-team membership and does not rely on login-time role claims.
     */
    boolean currentActorHasFirmWideRole(int shaleClientId, int firmWideRoleDefinitionId);

    List<FirmWideRoleDefinition> listFirmWideRolesForAdministration(int shaleClientId, int actorUserId);

    List<FirmWideRoleAssignment> listUserFirmWideRoleAssignments(int shaleClientId, int actorUserId, int userId);

    List<FirmWideRoleDefinition> listFirmWideRolesForUserView(int shaleClientId, int actorUserId);

    List<FirmWideRoleAssignment> listUserFirmWideRoleAssignmentsForView(int shaleClientId, int actorUserId, int userId);

    FirmWideRoleDefinition createFirmWideRole(CreateFirmWideRoleCommand command);

    FirmWideRoleDefinition renameFirmWideRole(RenameFirmWideRoleCommand command);

    FirmWideRoleDefinition setFirmWideRoleActive(FirmWideRoleLifecycleCommand command, boolean active);

    void deleteFirmWideRole(FirmWideRoleLifecycleCommand command);

    FirmWideRoleAssignment assignFirmWideRole(FirmWideRoleAssignmentCommand command);

    void removeFirmWideRoleAssignment(FirmWideRoleAssignmentLifecycleCommand command);

    FirmWideRoleAssignment restoreFirmWideRoleAssignment(FirmWideRoleAssignmentLifecycleCommand command);

    record FirmWideRoleDefinition(int id, int shaleClientId, String systemKey, String name,
            boolean active, boolean deleted, byte[] rowVer) {
        public FirmWideRoleDefinition { rowVer = rowVer == null ? null : rowVer.clone(); }
        @Override public byte[] rowVer() { return rowVer == null ? null : rowVer.clone(); }
        public boolean builtIn() { return "ADMIN".equals(systemKey) || "ATTORNEY".equals(systemKey); }
    }

    record FirmWideRoleAssignment(long id, int userId, int definitionId, String roleName,
            boolean deleted, byte[] rowVer) {
        public FirmWideRoleAssignment { rowVer = rowVer == null ? null : rowVer.clone(); }
        @Override public byte[] rowVer() { return rowVer == null ? null : rowVer.clone(); }
    }

    record CreateFirmWideRoleCommand(int shaleClientId, int actorUserId, String name) { }
    record RenameFirmWideRoleCommand(int shaleClientId, int actorUserId, int definitionId,
            String name, byte[] expectedRowVer) {
        public RenameFirmWideRoleCommand { expectedRowVer=expectedRowVer==null?null:expectedRowVer.clone(); }
        @Override public byte[] expectedRowVer(){return expectedRowVer==null?null:expectedRowVer.clone();}
    }
    record FirmWideRoleLifecycleCommand(int shaleClientId, int actorUserId, int definitionId,
            byte[] expectedRowVer) {
        public FirmWideRoleLifecycleCommand { expectedRowVer=expectedRowVer==null?null:expectedRowVer.clone(); }
        @Override public byte[] expectedRowVer(){return expectedRowVer==null?null:expectedRowVer.clone();}
    }
    record FirmWideRoleAssignmentCommand(int shaleClientId, int actorUserId, int userId, int definitionId) { }
    record FirmWideRoleAssignmentLifecycleCommand(int shaleClientId, int actorUserId, long assignmentId,
            byte[] expectedRowVer) {
        public FirmWideRoleAssignmentLifecycleCommand { expectedRowVer=expectedRowVer==null?null:expectedRowVer.clone(); }
        @Override public byte[] expectedRowVer(){return expectedRowVer==null?null:expectedRowVer.clone();}
    }

    record UserSummary(
            int id,
            String firstName,
            String lastName,
            String displayName,
            String email,
            String phone,
            String color,
            String initials,
            boolean admin,
            boolean attorney) {
    }

    record UserDetail(
            int id,
            int shaleClientId,
            String firstName,
            String lastName,
            String displayName,
            String email,
            String phone,
            String color,
            String initials,
            boolean admin,
            boolean attorney) {
    }
}
