package com.shale.server.runtime;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Step 3 placeholder resolver: no browser/mobile auth is trusted yet.
 */
public final class UnauthenticatedServerSessionResolver implements ServerSessionResolver {
    @Override
    public ServerSessionContext resolve(HttpServletRequest request) {
        return ServerSessionContext.unauthenticated();
    }
}
