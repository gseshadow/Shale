package com.shale.server.dto;

public record AuthenticatedUserResponse(
        boolean authenticated,
        int userId,
        int shaleClientId,
        String email,
        String displayName,
        String nameFirst,
        String nameLast) {
}
