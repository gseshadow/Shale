package com.shale.server.dto;

/**
 * Temporary login success shape for browser/mobile clients.
 *
 * <p>This DTO intentionally does not contain a JWT, cookie value, password hash,
 * or other sensitive authentication material.</p>
 */
public record LoginResponse(
        boolean authenticated,
        int userId,
        int shaleClientId,
        String displayName,
        String nameFirst,
        String nameLast,
        String todo) {
}
