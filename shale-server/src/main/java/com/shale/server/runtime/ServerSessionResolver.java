package com.shale.server.runtime;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Resolves browser/mobile request identity into a server session context.
 */
public interface ServerSessionResolver {
    ServerSessionContext resolve(HttpServletRequest request);
}
