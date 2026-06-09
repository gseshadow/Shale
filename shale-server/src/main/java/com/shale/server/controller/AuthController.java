package com.shale.server.controller;

import java.util.Objects;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.shale.core.model.User;
import com.shale.core.result.Result;
import com.shale.core.service.AuthServicePort;
import com.shale.server.dto.LoginErrorResponse;
import com.shale.server.dto.LoginRequest;
import com.shale.server.dto.LoginResponse;

@RestController
public final class AuthController {
    static final String TOKEN_SESSION_TODO =
            "TODO: token/session issuance is not implemented yet; this route only validates credentials.";
    private static final LoginErrorResponse INVALID_CREDENTIALS = new LoginErrorResponse(
            false,
            "invalid_credentials",
            "Invalid email or password.");

    private final AuthServicePort authServicePort;

    public AuthController(AuthServicePort authServicePort) {
        this.authServicePort = Objects.requireNonNull(authServicePort, "authServicePort");
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
        return ResponseEntity.ok(new LoginResponse(
                true,
                user.getId(),
                user.getShaleClientId(),
                displayName(user),
                user.getNameFirst(),
                user.getNameLast(),
                TOKEN_SESSION_TODO));
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
