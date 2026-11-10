package com.shale.server.oauth;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CimdClientIdentifierTest {
    @Test void preservesExactIdentityIncludingCasePortAndEncoding() {
        var plain = new CimdClientIdentifier("https://client.example/client.json");
        for (String value : List.of("HTTPS://CLIENT.example/client.json", "https://client.example:443/client.json",
                "https://client.example:8443/client.json", "https://client.example/%63lient.json")) {
            var identifier = new CimdClientIdentifier(value);
            assertEquals(value, identifier.value(), "CIMD identifiers must never be normalized");
            assertNotEquals(plain, identifier, "Different exact identifiers must not share cached identity");
        }
        assertEquals(plain, new CimdClientIdentifier(plain.value()));
    }

    @Test void rejectsNonHttpsRootAndAmbiguousOrInjectedComponents() {
        assertThrows(IllegalArgumentException.class, () -> new CimdClientIdentifier(null));
        for (String value : List.of("", "manual_client", "http://client.example/client.json", "//client.example/client.json",
                "https://client.example", "https://client.example/", "https://user@client.example/client.json",
                "https://client.example/client.json?x=1", "https://client.example/client.json?",
                "https://client.example/client.json#fragment", "https://client.example/client.json#",
                "https://client.example:0/client.json", "https://client.example:65536/client.json",
                "https://client.example:invalid/client.json", "https://client.example\\@evil.example/client.json",
                "https://client.example/a/../client.json", "https://client.example/./client.json",
                "https://client.example/%2e/client.json", "https://client.example/a/%2E%2e/client.json",
                " https://client.example/client.json", "https://client.example/client.json\r\nInjected: true",
                "https://client.example/é.json", "https://client.example/%zz.json")) {
            assertThrows(IllegalArgumentException.class, () -> new CimdClientIdentifier(value),
                    "Unsafe or unsupported identifier must fail locally");
        }
    }

    @Test void boundsInputAndDoesNotEchoRejectedMetadataInDiagnostics() {
        String prefix = "https://client.example/";
        String maximum = prefix + "x".repeat(CimdClientIdentifier.MAX_LENGTH - prefix.length());
        assertEquals(maximum, new CimdClientIdentifier(maximum).value());
        var failure = assertThrows(IllegalArgumentException.class, () -> new CimdClientIdentifier(maximum + "x"));
        assertFalse(failure.getMessage().contains("client.example"));
        assertNull(failure.getCause(), "Parser errors must not expose supplied URLs");
        assertEquals("CimdClientIdentifier[redacted]", new CimdClientIdentifier(maximum).toString());
    }

    @Test void syntaxAcceptanceDoesNotClaimDestinationSafetyOrResolveHosts() {
        // These MUST still fail the future fetcher's connection-time SSRF policy.
        for (String value : List.of("https://127.0.0.1/client.json", "https://[::1]/client.json",
                "https://metadata.internal/client.json", "https://does-not-exist.invalid/client.json")) {
            assertEquals(value, new CimdClientIdentifier(value).value());
        }
    }
}
