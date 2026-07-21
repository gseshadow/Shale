package com.shale.desktop.net;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class NegotiateClientLoggingTest {
    @Test
    void redactForLogRemovesSecretBearingQueryValues() {
        String redacted = NegotiateClient.redactForLog(
                "https://example.invalid/api/negotiate?code=secret&tenantId=7&access_token=token");

        assertTrue(redacted.contains("code=<redacted>"));
        assertTrue(redacted.contains("access_token=<redacted>"));
        assertFalse(redacted.contains("secret"));
        assertFalse(redacted.contains("access_token=token"));
    }
}
