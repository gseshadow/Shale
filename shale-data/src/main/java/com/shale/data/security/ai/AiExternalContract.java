package com.shale.data.security.ai;

/** Trusted composition selects a reviewed SQL contract. Never selected by a request or fallback. */
public enum AiExternalContract {
    CIMD_V1, EXTERNAL_V2;

    String procedure(String original) {
        if (!original.matches("AiCimd(?:LoadClient|LoadClientProof|BeginOAuth|FindOAuthIntent|ApproveOAuth|CompleteOAuth|DenyOAuth|ResolveCode|ResolveRefresh|ResolveRevocation|Redeem|Refresh|RevokeCredential|PublishMetadata|ResolvePublication|RevokeUnknown|GetOnboarding)V1"))
            throw new IllegalArgumentException("Unknown external application procedure.");
        return this == CIMD_V1 ? original : original.replaceFirst("AiCimd", "AiExternal").replaceFirst("V1$", "V2");
    }

    String registryResource() {
        return this == CIMD_V1 ? "/ai/registry-confinement-v1-catalog.sql" : "/ai/registry-external-v2-catalog.sql";
    }

    void requireWireNamespace(String value) {
        if (value != null && value.startsWith("dcr.")) {
            if (this != EXTERNAL_V2) throw new SecurityException("DCR requires the reviewed external contract.");
            new DcrClientIdentifier(value);
        }
    }
}
