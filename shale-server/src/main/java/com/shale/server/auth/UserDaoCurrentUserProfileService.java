package com.shale.server.auth;

import java.util.Optional;

import com.shale.data.dao.UserDao;
import com.shale.server.dto.AuthenticatedUserResponse;
import com.shale.server.runtime.ServerPrincipal;

public final class UserDaoCurrentUserProfileService implements CurrentUserProfileService {
    private final UserDao userDao;

    public UserDaoCurrentUserProfileService(UserDao userDao) {
        this.userDao = java.util.Objects.requireNonNull(userDao, "userDao");
    }

    @Override
    public Optional<AuthenticatedUserResponse> findCurrentUser(ServerPrincipal principal) {
        java.util.Objects.requireNonNull(principal, "principal");
        UserDao.UserDetailRow row = userDao.findById(principal.userId(), principal.shaleClientId());
        if (row == null) {
            return Optional.empty();
        }
        return Optional.of(new AuthenticatedUserResponse(
                true,
                row.id(),
                row.shaleClientId(),
                row.email(),
                row.displayName(),
                row.firstName(),
                row.lastName(),
                row.admin(),
                row.attorney(),
                row.initials(),
                row.color()));
    }
}
