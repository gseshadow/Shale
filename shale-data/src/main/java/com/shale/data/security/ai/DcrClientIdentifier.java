package com.shale.data.security.ai;

/** Exact server-generated issuer-local namespace, disjoint from CIMD URLs and manual IDs. */
public record DcrClientIdentifier(String value) {
    public DcrClientIdentifier {
        if (value == null || !value.matches("dcr\\.[0-9a-f]{64}"))
            throw new IllegalArgumentException("Exact server-generated DCR identity required.");
    }
    @Override public String toString() { return "DcrClientIdentifier[redacted]"; }
}
