package com.shale.data.security.ai;

import com.shale.core.runtime.DbSessionProvider;
import com.shale.core.security.ai.AiScope;
import com.shale.data.runtime.RuntimeSessionService;
import javax.sql.DataSource;
import java.sql.*;
import java.util.*;

/** Internal ACCESS authentication only. Never installed as a WEB or resource authenticator.
 * Every admission/recheck uses durable SQL; no request carrier or positive cache. */
public final class AiAccessTokenValidator {
    private final DbSessionProvider bootstrap;
    private final DataSource runtime;
    private final boolean enabled;
    private final String issuer, resource;

    public AiAccessTokenValidator(DbSessionProvider bootstrap, DataSource runtime, boolean enabled,
            String issuer, String resource) {
        this.bootstrap=Objects.requireNonNull(bootstrap); this.runtime=Objects.requireNonNull(runtime);
        this.enabled=enabled;
        AiIntegrationSecurityService.configuredHttps(issuer); AiIntegrationSecurityService.configuredHttps(resource);
        this.issuer=issuer; this.resource=resource;
    }
    record Owner(int tenant, int user, UUID integration, UUID grant, UUID session,
            long credential, String client, UUID key) {
        @Override public String toString() { return "AccessOwner[redacted]"; }
    }
    public VerifiedIntegrationContext validate(String accessToken) {
        // The raw value is never stored, passed to SQL, or copied into a context/error.
        byte[] digest;
        try { digest=AiOAuthCredentialStore.digest(accessToken); } catch(RuntimeException failure) { throw denied(); }
        try {
            requireEnabled();
            Owner owner=AiSql.one(AiSql.call(bootstrap,"ResolveAiAccessV1",r->new Owner(
                    r.getInt("ShaleClientId"),r.getInt("UserId"),AiSql.uuid(r,"IntegrationId"),
                    AiSql.uuid(r,"GrantId"),AiSql.uuid(r,"SessionId"),r.getLong("AccessCredentialId"),
                    r.getString("ClientId"),nullableUuid(r,"KeyId")),digest,issuer,resource));
            return gate(owner,3,UUID.randomUUID(),null,null);
        } catch (RuntimeException failure) { throw denied(); }
        finally { Arrays.fill(digest,(byte)0); }
    }
    /** Final-short-gate preparation only: returns a fresh narrowed snapshot after SQL commit/close.
     * It performs no disclosure and does not authorize network handoff; 3B.3 must compose that boundary. */
    public VerifiedIntegrationContext recheck(VerifiedIntegrationContext context) {
        try {
            requireEnabled();
            if(context==null || context.credentialId()==null || !issuer.equals(context.issuer()) || !resource.equals(context.resource())) throw denied();
            return gate(new Owner(context.shaleClientId(),context.actingUserId(),context.integrationId(),
                    context.grantId(),context.delegatedSessionId(),context.credentialId(),context.oauthClientId(),
                    context.authenticatingKeyId()),AiScope.mask(context.effectiveScopes()),context.correlationId(),
                    context.invocationId(),context.operationScope());
        } catch(RuntimeException failure) { throw denied(); }
    }
    private VerifiedIntegrationContext gate(Owner owner,int ceiling,UUID correlation,UUID invocation,AiScope operation) {
        if(owner.tenant()<=0 || owner.user()<=0 || owner.credential()<=0) throw denied();
        var initializer=new RuntimeSessionService(runtime);
        initializer.initialize(owner.tenant(),owner.user());
        VerifiedIntegrationContext result;
        try(Connection c=initializer.getConnection(5)) {
            if(!c.getAutoCommit()) throw denied();
            // Both existing mutable context keys must be correct before any integration-authorized SQL.
            try(var p=c.prepareStatement("SELECT TRY_CONVERT(int,SESSION_CONTEXT(N'ShaleClientId')), TRY_CONVERT(int,SESSION_CONTEXT(N'PrincipalUserId'))")) {
                p.setQueryTimeout(5);
                try(var rows=p.executeQuery()) {
                    if(!rows.next() || rows.getInt(1)!=owner.tenant() || rows.getInt(2)!=owner.user() || rows.next()) throw denied();
                }
            }
            result=AiSql.one(AiSql.call(c,"AiValidateAccessV1",r->new VerifiedIntegrationContext(
                    owner.tenant(),owner.user(),owner.integration(),owner.grant(),owner.session(),
                    AiScope.fromMask(r.getInt("ScopeMask")),operation,owner.client(),owner.key(),owner.credential(),
                    issuer,resource,r.getLong("CapabilityRevision"),r.getLong("PolicyRevision"),
                    r.getLong("IntegrationRevision"),r.getLong("GrantRevision"),AiSql.instant(r,"Deadline"),
                    correlation,invocation),owner.credential(),owner.integration(),owner.grant(),owner.session(),
                    owner.client(),AiSql.nullable(owner.key(),Types.VARCHAR),issuer,resource,ceiling));
        } catch(SQLException failure) { throw denied(); }
        requireEnabled();
        return result; // Only after procedure completion and successful lease close.
    }
    private void requireEnabled() { if(!enabled || Thread.currentThread().isInterrupted()) throw denied(); }
    private static UUID nullableUuid(ResultSet r,String name)throws SQLException {
        String value=r.getString(name);return value==null?null:UUID.fromString(value);
    }
    private static SecurityException denied() { return new SecurityException("Integration authentication failed."); }
}
