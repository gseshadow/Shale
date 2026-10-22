package com.shale.data.security.ai;

import com.shale.core.runtime.DbSessionProvider;
import java.util.*;

/** Provisional identity only. Never initialize business work from a client claim or this object alone. */
public final class AiBootstrapResolver {
    public record ProvisionalOwner(int shaleClientId, int userId, UUID integrationId, UUID grantId, UUID sessionId) {}
    private final DbSessionProvider bootstrap;
    private final String issuer, resource;
    public AiBootstrapResolver(DbSessionProvider bootstrap, String issuer, String resource) {
        this.bootstrap = Objects.requireNonNull(bootstrap);
        AiIntegrationSecurityService.configuredHttps(issuer); AiIntegrationSecurityService.configuredHttps(resource);
        this.issuer = issuer; this.resource = resource;
    }
    public Optional<ProvisionalOwner> resolveAccessDigest(byte[] digest) {
        if (digest == null || digest.length != 32) throw new IllegalArgumentException("SHA-256 digest required.");
        var rows = AiSql.call(bootstrap, "ResolveAiCredentialV1", r -> new ProvisionalOwner(r.getInt("ShaleClientId"),
                r.getInt("UserId"), AiSql.uuid(r,"IntegrationId"), AiSql.uuid(r,"GrantId"), AiSql.uuid(r,"SessionId")), digest.clone(), issuer, resource);
        if (rows.size() > 1) throw new SecurityException("Ambiguous AI identity.");
        return rows.stream().findFirst();
    }
}
