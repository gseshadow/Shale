package com.shale.server.auth;

import java.util.Optional;

import com.shale.server.dto.AuthenticatedUserResponse;
import com.shale.server.runtime.ServerPrincipal;

public interface CurrentUserProfileService {
    Optional<AuthenticatedUserResponse> findCurrentUser(ServerPrincipal principal);
}
