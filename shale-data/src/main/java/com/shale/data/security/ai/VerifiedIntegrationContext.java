package com.shale.data.security.ai;

import com.shale.core.security.ai.AiScope;
import java.util.Objects;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/** Immutable snapshot from a live SQL gate, never a bearer or ordinary REST principal.
 * Future disclosure must revalidate it; this snapshot alone authorizes no Case operation. */
public final class VerifiedIntegrationContext {
    private final int shaleClientId, actingUserId;
    private final UUID integrationId, grantId, delegatedSessionId, invocationId, correlationId;
    private final Set<AiScope> effectiveScopes;
    private final AiScope operationScope;

    private final String oauthClientId, issuer, resource;
    private final UUID authenticatingKeyId;
    private final Long credentialId;
    private final long capabilityRevision, policyRevision, integrationRevision, grantRevision;
    private final Instant deadline;

    VerifiedIntegrationContext(int tenant, int user, UUID integration, UUID grant, UUID session,
            Set<AiScope> scopes, AiScope operationScope) {
        this(tenant,user,integration,grant,session,scopes,operationScope,null,null,null,null,null,
                0,0,0,0,null,UUID.randomUUID(),UUID.randomUUID());
    }
    VerifiedIntegrationContext(int tenant,int user,UUID integration,UUID grant,UUID session,
            Set<AiScope> scopes,AiScope operationScope,String client,UUID key,Long credential,
            String issuer,String resource,long capability,long policy,long registration,long grantVersion,
            Instant deadline,UUID correlation,UUID invocation) {
        if (tenant <= 0 || user <= 0) throw new SecurityException("Verified AI identity required.");
        shaleClientId = tenant; actingUserId = user;
        integrationId = Objects.requireNonNull(integration); grantId = Objects.requireNonNull(grant);
        delegatedSessionId = Objects.requireNonNull(session); effectiveScopes = Set.copyOf(scopes);
        this.operationScope = operationScope;
        if (operationScope!=null && !effectiveScopes.contains(operationScope)) throw new SecurityException("AI scope unavailable.");
        if (credential!=null && (credential<=0 || client==null || !client.matches("[A-Za-z0-9_-]{1,64}")
                || issuer==null || issuer.length()>256 || resource==null || resource.length()>256 || deadline==null
                || capability<=0 || policy<=0 || registration<=0 || grantVersion<=0))
            throw new SecurityException("Verified ACCESS provenance required.");
        oauthClientId=client; authenticatingKeyId=key; credentialId=credential;
        this.issuer=issuer; this.resource=resource; capabilityRevision=capability; policyRevision=policy;
        integrationRevision=registration; grantRevision=grantVersion; this.deadline=deadline;
        invocationId=invocation; correlationId=Objects.requireNonNull(correlation);
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
    public String oauthClientId() { return oauthClientId; }
    public UUID authenticatingKeyId() { return authenticatingKeyId; }
    public Long credentialId() { return credentialId; }
    public String issuer() { return issuer; }
    public String resource() { return resource; }
    public long capabilityRevision() { return capabilityRevision; }
    public long policyRevision() { return policyRevision; }
    public long integrationRevision() { return integrationRevision; }
    public long grantRevision() { return grantRevision; }
    public Instant deadline() { return deadline; }
    @Override public String toString() { return "VerifiedIntegrationContext[redacted]"; }
}
