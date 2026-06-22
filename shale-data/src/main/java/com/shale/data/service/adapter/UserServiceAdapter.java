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
}
