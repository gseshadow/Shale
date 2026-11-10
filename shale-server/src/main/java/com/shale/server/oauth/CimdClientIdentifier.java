package com.shale.server.oauth;

import java.net.URI;

/**
 * Bounded CIMD identifier syntax only; never a fetch authorization, client registration or tenant grant.
 * Preserves the exact identifier for simple string comparison. No DNS, network or normalization occurs.
 * The future fetcher must separately enforce destination/SSRF policy at connection time.
 */
public record CimdClientIdentifier(String value) {
    public static final int MAX_LENGTH = 2048;

    public CimdClientIdentifier {
        if (value == null || value.length() > MAX_LENGTH ||
                value.chars().anyMatch(c -> c <= 32 || c >= 127)) throw invalid();
        URI uri;
        try { uri = URI.create(value); }
        catch (IllegalArgumentException failure) { throw invalid(); }
        // No query is a Shale profile restriction (SHOULD NOT in CIMD draft-03).
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null ||
                uri.getRawUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null ||
                uri.getPort() == 0 || uri.getPort() > 65535 ||
                uri.getRawPath() == null || uri.getRawPath().isEmpty() || "/".equals(uri.getRawPath())) throw invalid();
        // Also reject percent-encoded dot segments; do not silently repair an identifier.
        for (String segment : uri.getPath().split("/", -1)) {
            if (".".equals(segment) || "..".equals(segment)) throw invalid();
        }
    }

    @Override public String toString() { return "CimdClientIdentifier[redacted]"; }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("A bounded HTTPS CIMD identifier with a non-root path is required.");
    }
}
