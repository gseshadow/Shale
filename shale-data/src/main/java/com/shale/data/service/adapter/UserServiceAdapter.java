package com.shale.data.service.adapter;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.shale.core.service.UserServicePort;
import com.shale.data.dao.UserDao;

public final class UserServiceAdapter implements UserServicePort {
    private final UserDao userDao;

    public UserServiceAdapter(UserDao userDao) {
        this.userDao = Objects.requireNonNull(userDao, "userDao");
    }

    @Override
    public List<UserSummary> listTenantUsers(int shaleClientId) {
        return userDao.listUsersForTenant(shaleClientId).stream()
                .map(row -> {
                    UserDao.UserDetailRow detail = userDao.findById(row.id(), shaleClientId);
                    return new UserSummary(
                            row.id(),
                            row.firstName(),
                            row.lastName(),
                            row.displayName(),
                            row.email(),
                            row.phone(),
                            row.color(),
                            row.initials(),
                            detail != null && detail.admin(),
                            detail != null && detail.attorney());
                })
                .toList();
    }

    @Override
    public Optional<UserDetail> getUserDetail(int userId, int shaleClientId) {
        UserDao.UserDetailRow row = userDao.findById(userId, shaleClientId);
        if (row == null) {
            return Optional.empty();
        }
        return Optional.of(new UserDetail(
                row.id(),
                row.shaleClientId(),
                row.firstName(),
                row.lastName(),
                row.displayName(),
                row.email(),
                row.phone(),
                row.color(),
                row.initials(),
                row.admin(),
                row.attorney()));
    }

    @Override
    public boolean currentActorHasFirmWideRole(int shaleClientId, int firmWideRoleDefinitionId) {
        return userDao.currentActorHasFirmWideRole(shaleClientId, firmWideRoleDefinitionId);
    }

    @Override public List<FirmWideRoleDefinition> listFirmWideRolesForAdministration(int tenant,int actor){return userDao.listFirmWideRolesForAdministration(tenant,actor);}
    @Override public List<FirmWideRoleAssignment> listUserFirmWideRoleAssignments(int tenant,int actor,int user){return userDao.listUserFirmWideRoleAssignments(tenant,actor,user);}
    @Override public FirmWideRoleDefinition createFirmWideRole(CreateFirmWideRoleCommand command){return userDao.createFirmWideRole(command);}
    @Override public FirmWideRoleDefinition renameFirmWideRole(RenameFirmWideRoleCommand command){return userDao.renameFirmWideRole(command);}
    @Override public FirmWideRoleDefinition setFirmWideRoleActive(FirmWideRoleLifecycleCommand command,boolean active){return userDao.setFirmWideRoleActive(command,active);}
    @Override public void deleteFirmWideRole(FirmWideRoleLifecycleCommand command){userDao.deleteFirmWideRole(command);}
    @Override public FirmWideRoleAssignment assignFirmWideRole(FirmWideRoleAssignmentCommand command){return userDao.assignFirmWideRole(command);}
    @Override public void removeFirmWideRoleAssignment(FirmWideRoleAssignmentLifecycleCommand command){userDao.removeFirmWideRoleAssignment(command);}
    @Override public FirmWideRoleAssignment restoreFirmWideRoleAssignment(FirmWideRoleAssignmentLifecycleCommand command){return userDao.restoreFirmWideRoleAssignment(command);}
}
