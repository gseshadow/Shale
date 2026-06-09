package com.shale.server.controller;

import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.shale.server.runtime.ServerPrincipal;
import com.shale.server.runtime.ServerRuntimeSessionState;

/**
 * TEMPORARY development-only proof endpoint for request-context simulation.
 *
 * <p>This is not production authentication and must not issue or validate JWTs,
 * cookies, Azure auth, or browser sessions.</p>
 */
@RestController
@Profile("dev")
public final class DevIdentityController {
    private final ServerRuntimeSessionState runtimeSessionState;

    public DevIdentityController(ServerRuntimeSessionState runtimeSessionState) {
        this.runtimeSessionState = java.util.Objects.requireNonNull(runtimeSessionState, "runtimeSessionState");
    }

    @GetMapping("/api/dev/whoami")
    public DevWhoamiResponse whoami() {
        ServerPrincipal principal = runtimeSessionState.requirePrincipal();
        return new DevWhoamiResponse(true, principal.userId(), principal.shaleClientId());
    }

    public record DevWhoamiResponse(boolean authenticated, int userId, int shaleClientId) {
    }
}
