package com.shale.server.oauth;

import com.shale.data.security.ai.DcrClientIdentifier;

/** Exact external wire identity. Its source is determined by disjoint syntax, never caller metadata. */
public record OAuthApplicationIdentifier(String value) {
    public enum Source { CIMD, DCR }
    public OAuthApplicationIdentifier {
        if (value != null && value.startsWith("dcr.")) new DcrClientIdentifier(value);
        else new CimdClientIdentifier(value);
    }
    public Source source() { return value.startsWith("dcr.") ? Source.DCR : Source.CIMD; }
    public CimdClientIdentifier cimd() {
        if (source() != Source.CIMD) throw new SecurityException("DCR is stored metadata, never a CIMD URL.");
        return new CimdClientIdentifier(value);
    }
    @Override public String toString() { return "OAuthApplicationIdentifier[redacted]"; }
}
