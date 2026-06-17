package com.shale.server.runtime;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import jakarta.servlet.http.HttpServletRequest;

public final class BearerTokenServerSessionResolver implements ServerSessionResolver {
    private static final String AUTHORIZATION = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final ShaleAuthTokenService tokenService;

    public BearerTokenServerSessionResolver(ShaleAuthTokenService tokenService) {
        this.tokenService = java.util.Objects.requireNonNull(tokenService, "tokenService");
    }

    @Override
    public ServerSessionContext resolve(HttpServletRequest request) {
        if (request == null) {
            return ServerSessionContext.unauthenticated();
        }
        String authorization = request.getHeader(AUTHORIZATION);
        if (authorization == null || authorization.isBlank()) {
            return ServerSessionContext.unauthenticated();
        }
        if (!authorization.startsWith(BEARER_PREFIX)) {
            throw invalidToken();
        }
        String token = authorization.substring(BEARER_PREFIX.length()).trim();
        return tokenService.verify(token)
                .map(ServerSessionContext::authenticated)
                .orElseThrow(BearerTokenServerSessionResolver::invalidToken);
    }

    private static ResponseStatusException invalidToken() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or expired authentication token.");
    }
}
