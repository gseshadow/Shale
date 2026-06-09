package com.shale.server.dto;

/**
 * Safe login failure shape that does not disclose whether an email exists.
 */
public record LoginErrorResponse(boolean authenticated, String error, String message) {
}
