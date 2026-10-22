package com.shale.data.security.ai;

import com.shale.core.security.ai.AiScope;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Immutable snapshot from a live SQL gate, never a bearer or ordinary REST principal.
 * Future disclosure must revalidate it; this snapshot alone authorizes no Case operation. */
public final class VerifiedIntegrationContext {
    private final int shaleClientId, actingUserId;
    private final UUID integrationId, grantId, delegatedSessionId, invocationId, correlationId;
    private final Set<AiScope> effectiveScopes;
    private final AiScope operationScope;

    VerifiedIntegrationContext(int tenant, int user, UUID integration, UUID grant, UUID session,
            Set<AiScope> scopes, AiScope operationScope) {
        if (tenant <= 0 || user <= 0) throw new SecurityException("Verified AI identity required.");
        shaleClientId = tenant; actingUserId = user;
        integrationId = Objects.requireNonNull(integration); grantId = Objects.requireNonNull(grant);
        delegatedSessionId = Objects.requireNonNull(session); effectiveScopes = Set.copyOf(scopes);
        this.operationScope = Objects.requireNonNull(operationScope);
        if (!effectiveScopes.contains(operationScope)) throw new SecurityException("AI scope unavailable.");
        invocationId = UUID.randomUUID(); correlationId = UUID.randomUUID();
    }
    public int shaleClientId() { return shaleClientId; }
    public int actingUserId() { return actingUserId; }
    public UUID integrationId() { return integrationId; }
    public UUID grantId() { return grantId; }
    public UUID delegatedSessionId() { return delegatedSessionId; }
    public Set<AiScope> effectiveScopes() { return effectiveScopes; }
    public AiScope operationScope() { return operationScope; }
    public UUID invocationId() { return invocationId; }
    public UUID correlationId() { return correlationId; }
    @Override public String toString() { return "VerifiedIntegrationContext[redacted]"; }
}
