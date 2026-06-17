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
import com.shale.server.auth.CurrentUserProfileService;
import com.shale.server.dto.AuthenticatedUserResponse;
import com.shale.server.dto.LoginErrorResponse;
import com.shale.server.dto.LoginRequest;
import com.shale.server.dto.LoginResponse;
import com.shale.server.dto.LogoutResponse;
import com.shale.server.dto.RefreshResponse;
import com.shale.server.runtime.BearerTokenServerSessionResolver;
import com.shale.server.runtime.ServerPrincipal;
import com.shale.server.runtime.ServerRuntimeSessionState;
import com.shale.server.runtime.ShaleAuthTokenService;
import com.shale.server.runtime.TokenRevocationStore;
import com.shale.server.runtime.VerifiedAuthToken;

import jakarta.servlet.http.HttpServletRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@Tag(name = "Authentication", description = "Login, bearer-token session, logout, refresh, and current-user endpoints")
public final class AuthController {
    private static final LoginErrorResponse INVALID_CREDENTIALS = new LoginErrorResponse(
            false,
            "invalid_credentials",
            "Invalid email or password.");

    private final AuthServicePort authServicePort;
    private final ShaleAuthTokenService tokenService;
    private final TokenRevocationStore revocationStore;
    private final ServerRuntimeSessionState runtimeSessionState;
    private final CurrentUserProfileService currentUserProfileService;

    public AuthController(
            AuthServicePort authServicePort,
            ShaleAuthTokenService tokenService,
            TokenRevocationStore revocationStore,
            ServerRuntimeSessionState runtimeSessionState,
            CurrentUserProfileService currentUserProfileService) {
        this.authServicePort = Objects.requireNonNull(authServicePort, "authServicePort");
        this.tokenService = Objects.requireNonNull(tokenService, "tokenService");
        this.revocationStore = Objects.requireNonNull(revocationStore, "revocationStore");
        this.runtimeSessionState = Objects.requireNonNull(runtimeSessionState, "runtimeSessionState");
        this.currentUserProfileService = Objects.requireNonNull(currentUserProfileService, "currentUserProfileService");
    }

    @Operation(summary = "Login", description = "Authenticates an existing Shale user by email/password and returns a server-issued bearer token plus a safe user profile.")
    @PostMapping("/api/auth/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        LoginRequest validRequest = ApiValidation.requireValidLogin(request);

        Result<User> result = authServicePort.authenticate(validRequest.email(), validRequest.password());
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

    @Operation(summary = "Logout", description = "Revokes the current bearer token when one is present. Raw tokens are never stored by the server.")
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/api/auth/logout")
    public LogoutResponse logout(HttpServletRequest request) {
        VerifiedAuthToken token = currentToken(request);
        if (token != null) {
            revocationStore.revoke(token.tokenId(), token.expiresAtEpochSeconds());
        }
        return new LogoutResponse(token != null, "Logged out.");
    }

    @Operation(summary = "Refresh access token", description = "Issues a replacement access token when the current token is valid and not revoked.")
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/api/auth/refresh")
    public ResponseEntity<?> refresh(HttpServletRequest request) {
        VerifiedAuthToken token = currentToken(request);
        if (token == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new LogoutResponse(false, "Authentication is required."));
        }
        revocationStore.revoke(token.tokenId(), token.expiresAtEpochSeconds());
        String refreshed = tokenService.issue(token.principal());
        return ResponseEntity.ok(new RefreshResponse(true, "Bearer", refreshed, tokenService.ttlSeconds()));
    }

    @Operation(summary = "Current authenticated user", description = "Returns a safe current-user profile resolved from the authenticated server principal.")
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/api/auth/me")
    public AuthenticatedUserResponse me() {
        ServerPrincipal principal = runtimeSessionState.requirePrincipal();
        return currentUserProfileService.findCurrentUser(principal)
                .orElseGet(() -> principalResponse(principal));
    }

    private VerifiedAuthToken currentToken(HttpServletRequest request) {
        String rawToken = BearerTokenServerSessionResolver.bearerToken(request);
        if (rawToken == null) {
            return null;
        }
        return tokenService.verifyToken(rawToken)
                .filter(token -> !revocationStore.isRevoked(token.tokenId()))
                .orElse(null);
    }

    private static AuthenticatedUserResponse userResponse(User user) {
        return new AuthenticatedUserResponse(
                true,
                user.getId(),
                user.getShaleClientId(),
                user.getEmail(),
                displayName(user),
                user.getNameFirst(),
                user.getNameLast(),
                user.isAdmin(),
                user.isAttorney(),
                user.getInitials(),
                user.getColor());
    }

    private static AuthenticatedUserResponse principalResponse(ServerPrincipal principal) {
        return new AuthenticatedUserResponse(
                true,
                principal.userId(),
                principal.shaleClientId(),
                principal.email(),
                null,
                null,
                null,
                false,
                false,
                null,
                null);
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
