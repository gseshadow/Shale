package com.shale.server.runtime;

import jakarta.servlet.http.HttpServletRequest;

/**
 * TEMPORARY development-only resolver for proving request-scoped tenant/user DB access.
 *
 * <p>This is not authentication. It trusts local development headers only when the
 * Spring {@code dev} profile wires this resolver. Do not use for production,
 * browser sessions, cookies, JWTs, or Azure auth.</p>
 */
public final class DevelopmentHeaderServerSessionResolver implements ServerSessionResolver {
    public static final String USER_ID_HEADER = "X-Shale-UserId";
    public static final String TENANT_ID_HEADER = "X-Shale-TenantId";

    @Override
    public ServerSessionContext resolve(HttpServletRequest request) {
        if (request == null) {
            return ServerSessionContext.unauthenticated();
        }

        Integer userId = positiveIntHeader(request, USER_ID_HEADER);
        Integer tenantId = positiveIntHeader(request, TENANT_ID_HEADER);
        if (userId == null || tenantId == null) {
            return ServerSessionContext.unauthenticated();
        }

        return ServerSessionContext.authenticated(new ServerPrincipal(userId, tenantId, null));
    }

    private static Integer positiveIntHeader(HttpServletRequest request, String headerName) {
        String raw = request.getHeader(headerName);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            int value = Integer.parseInt(raw.trim());
            return value > 0 ? value : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
