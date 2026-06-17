package com.shale.server.runtime;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import jakarta.servlet.http.HttpServletRequest;

public final class BearerTokenServerSessionResolver implements ServerSessionResolver {
    public static final String AUTHORIZATION = "Authorization";
    public static final String BEARER_PREFIX = "Bearer ";

    private final ShaleAuthTokenService tokenService;
    private final TokenRevocationStore revocationStore;

    public BearerTokenServerSessionResolver(ShaleAuthTokenService tokenService, TokenRevocationStore revocationStore) {
        this.tokenService = java.util.Objects.requireNonNull(tokenService, "tokenService");
        this.revocationStore = java.util.Objects.requireNonNull(revocationStore, "revocationStore");
    }

    @Override
    public ServerSessionContext resolve(HttpServletRequest request) {
        if (request == null) {
            return ServerSessionContext.unauthenticated();
        }
        String token = bearerToken(request);
        if (token == null) {
            return ServerSessionContext.unauthenticated();
        }
        VerifiedAuthToken verifiedToken = tokenService.verifyToken(token)
                .orElseThrow(BearerTokenServerSessionResolver::invalidToken);
        if (revocationStore.isRevoked(verifiedToken.tokenId())) {
            throw invalidToken();
        }
        return ServerSessionContext.authenticated(verifiedToken.principal());
    }

    public static String bearerToken(HttpServletRequest request) {
        String authorization = request == null ? null : request.getHeader(AUTHORIZATION);
        if (authorization == null || authorization.isBlank()) {
            return null;
        }
        if (!authorization.startsWith(BEARER_PREFIX)) {
            throw invalidToken();
        }
        return authorization.substring(BEARER_PREFIX.length()).trim();
    }

    private static ResponseStatusException invalidToken() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or expired authentication token.");
    }
}
