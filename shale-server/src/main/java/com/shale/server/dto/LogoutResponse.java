package com.shale.server.dto;

public record LogoutResponse(boolean revoked, String message) {
}
