package com.shale.data.security.ai;

import com.shale.core.runtime.DbSessionProvider;
import java.net.URI;
import java.util.Objects;
import java.util.UUID;
import static com.shale.data.security.ai.AiIntegrationSecurityService.RowVersion;

/** Offline registry control plane. No runtime composition, tenant inventory or OAuth authority.
 * The separately governed publisher must supply a digest of validated metadata AND public keys.
 * Publishing even unchanged bytes advances the revision, requiring fresh tenant approval. */
public final class AiApplicationRegistryService {
    private final DbSessionProvider publisher;
    private final String issuer, resource;

    public AiApplicationRegistryService(DbSessionProvider publisher, String issuer, String resource) {
        this.publisher = Objects.requireNonNull(publisher);
        AiIntegrationSecurityService.configuredHttps(issuer);
        AiIntegrationSecurityService.configuredHttps(resource);
        this.issuer = issuer; this.resource = resource;
    }
    public record Application(UUID applicationId, long revision, boolean disabled, RowVersion rowVersion) {}

    public Application publishCimd(String exactIdentifier, byte[] validatedPublicationDigest, int methodMask,
                                   boolean disabled, RowVersion expected) {
        URI uri;
        try { uri = URI.create(Objects.requireNonNull(exactIdentifier)); }
        catch (IllegalArgumentException failure) { throw new IllegalArgumentException("Invalid CIMD identity."); }
        // PR 2's parser/SSRF checks remain mandatory at the future retrieval adapter. No fetch here.
        if (exactIdentifier.length() > 2048 || !uri.isAbsolute() || !"https".equals(uri.getScheme()) ||
                uri.getHost() == null || uri.getRawUserInfo() != null || uri.getRawFragment() != null ||
                uri.getRawQuery() != null || uri.getRawPath() == null || uri.getRawPath().length() < 2 ||
                !exactIdentifier.chars().allMatch(c -> c > 32 && c < 127) ||
                validatedPublicationDigest == null || validatedPublicationDigest.length != 32 || methodMask < 1 || methodMask > 3)
            throw new IllegalArgumentException("Validated bounded CIMD publication required.");
        return AiSql.one(AiSql.call(publisher, "AiPublishApplicationV1", r -> new Application(
                AiSql.uuid(r,"ApplicationId"),r.getLong("Revision"),r.getBoolean("Disabled"),new RowVersion(r.getBytes("RowVer"))),
                issuer,resource,exactIdentifier,validatedPublicationDigest.clone(),methodMask,disabled,
                expected == null ? null : expected.bytes(),UUID.randomUUID()));
    }
}
