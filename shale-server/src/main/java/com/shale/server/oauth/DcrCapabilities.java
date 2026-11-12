package com.shale.server.oauth;

import com.shale.data.security.ai.*;
import java.util.Objects;

/** Explicit operator-supplied capabilities; no credentials, datasource fallback or pool construction. */
public record DcrCapabilities(AiDcrApplicationStore registrar,AiDcrApplicationStore metadata,AiDcrAdmissionStore admission) {
    public DcrCapabilities {Objects.requireNonNull(registrar);Objects.requireNonNull(metadata);Objects.requireNonNull(admission);if(registrar.contract()!=AiExternalContract.DCR_V3)throw new IllegalArgumentException("C2 registrar required.");}
    @Override public String toString(){return "DcrCapabilities[redacted]";}
}
