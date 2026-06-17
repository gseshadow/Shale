package com.shale.server.dto;

public record RefreshResponse(boolean refreshed, String tokenType, String accessToken, long expiresInSeconds) {
}
