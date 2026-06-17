package com.shale.server.controller;

import java.util.Objects;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.shale.core.model.User;
import com.shale.core.result.Result;
import com.shale.core.service.AuthServicePort;
import com.shale.server.dto.AuthenticatedUserResponse;
import com.shale.server.dto.LoginErrorResponse;
import com.shale.server.dto.LoginRequest;
import com.shale.server.dto.LoginResponse;
import com.shale.server.runtime.ServerPrincipal;
import com.shale.server.runtime.ServerRuntimeSessionState;
import com.shale.server.runtime.ShaleAuthTokenService;

@RestController
public final class AuthController {
    private static final LoginErrorResponse INVALID_CREDENTIALS = new LoginErrorResponse(
            false,
            "invalid_credentials",
            "Invalid email or password.");

    private final AuthServicePort authServicePort;
    private final ShaleAuthTokenService tokenService;
    private final ServerRuntimeSessionState runtimeSessionState;

    public AuthController(
            AuthServicePort authServicePort,
            ShaleAuthTokenService tokenService,
            ServerRuntimeSessionState runtimeSessionState) {
        this.authServicePort = Objects.requireNonNull(authServicePort, "authServicePort");
        this.tokenService = Objects.requireNonNull(tokenService, "tokenService");
        this.runtimeSessionState = Objects.requireNonNull(runtimeSessionState, "runtimeSessionState");
    }

    @PostMapping("/api/auth/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        String email = request == null ? null : request.email();
        String password = request == null ? null : request.password();

        Result<User> result = authServicePort.authenticate(email, password);
        if (!result.isOk()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(INVALID_CREDENTIALS);
        }

        User user = result.value().orElseThrow();
        ServerPrincipal principal = new ServerPrincipal(user.getId(), user.getShaleClientId(), user.getEmail());
        String token = tokenService.issue(principal);
        return ResponseEntity.ok(new LoginResponse(
                true,
                "Bearer",
                token,
                tokenService.ttlSeconds(),
                userResponse(user)));
    }

    @GetMapping("/api/auth/me")
    public AuthenticatedUserResponse me() {
        ServerPrincipal principal = runtimeSessionState.requirePrincipal();
        return new AuthenticatedUserResponse(
                true,
                principal.userId(),
                principal.shaleClientId(),
                principal.email(),
                null,
                null,
                null);
    }

    private static AuthenticatedUserResponse userResponse(User user) {
        return new AuthenticatedUserResponse(
                true,
                user.getId(),
                user.getShaleClientId(),
                user.getEmail(),
                displayName(user),
                user.getNameFirst(),
                user.getNameLast());
    }

    private static String displayName(User user) {
        String first = trimToNull(user.getNameFirst());
        String last = trimToNull(user.getNameLast());
        if (first != null && last != null) {
            return first + " " + last;
        }
        if (first != null) {
            return first;
        }
        if (last != null) {
            return last;
        }
        return null;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
