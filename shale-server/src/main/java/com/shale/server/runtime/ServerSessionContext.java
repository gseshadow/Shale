package com.shale.server.runtime;

import java.util.Optional;

/**
 * Per-request server identity and tenant context.
 *
 * <p>Step 3 only models the shape of this context. Real browser/mobile auth will
 * populate it later after validating a session/token and resolving the user and
 * tenant from durable server-side state.</p>
 */
public final class ServerSessionContext {
    private static final ServerSessionContext UNAUTHENTICATED = new ServerSessionContext(null);

    private final ServerPrincipal principal;

    private ServerSessionContext(ServerPrincipal principal) {
        this.principal = principal;
    }

    public static ServerSessionContext unauthenticated() {
        return UNAUTHENTICATED;
    }

    public static ServerSessionContext authenticated(ServerPrincipal principal) {
        return new ServerSessionContext(java.util.Objects.requireNonNull(principal, "principal"));
    }

    public Optional<ServerPrincipal> principal() {
        return Optional.ofNullable(principal);
    }

    public boolean authenticated() {
        return principal != null;
    }
}
