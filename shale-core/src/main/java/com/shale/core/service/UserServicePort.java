package com.shale.core.service;

import java.util.List;
import java.util.Optional;

public interface UserServicePort {
    List<UserSummary> listTenantUsers(int shaleClientId);

    Optional<UserDetail> getUserDetail(int userId, int shaleClientId);

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
