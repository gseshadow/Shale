package com.shale.server.dto;

public record LoginResponse(
        boolean authenticated,
        String tokenType,
        String accessToken,
        long expiresInSeconds,
        AuthenticatedUserResponse user) {
}
