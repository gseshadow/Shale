package com.shale.server.dto;

/**
 * Browser/mobile login request skeleton.
 *
 * <p>The password is accepted only for credential validation and must not be
 * logged, echoed, or persisted by the server controller.</p>
 */
public record LoginRequest(String email, String password) {
}
