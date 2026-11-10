package com.shale.data.security.ai;

import com.shale.core.runtime.DbSessionProvider;
import com.shale.core.security.ai.AiScope;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import static com.shale.data.security.ai.AiIntegrationSecurityService.RowVersion;

/** Backend-only approval foundation. Provider derives both SQL keys from authenticated identity.
 * Approval creates a suspended integration; it cannot consent, issue credentials or activate OAuth. */
public final class AiTenantApplicationService {
    private final DbSessionProvider tenant;
    public AiTenantApplicationService(DbSessionProvider tenant) { this.tenant = Objects.requireNonNull(tenant); }
    public enum Decision { APPROVE, DENY, REVOKE, REDUCE }
    public record Approval(UUID approvalId, UUID applicationId, long applicationRevision, UUID integrationId,
                           String state, Set<AiScope> ceiling, Instant expiresAt, RowVersion rowVersion) {
        public Approval { ceiling = Set.copyOf(ceiling); }
    }
    public Approval request(UUID application, RowVersion publicationVersion, Set<AiScope> scopes) {
        int mask = AiScope.mask(Objects.requireNonNull(scopes));
        if(mask == 0) throw new IllegalArgumentException("Requested scopes required.");
        return AiSql.one(AiSql.call(tenant,"AiRequestApplicationV1",AiTenantApplicationService::approval,
                Objects.requireNonNull(application),Objects.requireNonNull(publicationVersion).bytes(),mask,UUID.randomUUID()));
    }
    public Approval decide(UUID approval, RowVersion expected, Decision decision, Set<AiScope> ceiling, int days) {
        Objects.requireNonNull(decision);
        int mask = AiScope.mask(Objects.requireNonNull(ceiling));
        if(decision == Decision.APPROVE && (mask == 0 || days < 1 || days > 90))
            throw new IllegalArgumentException("Approval needs scopes and 1–90 days.");
        return AiSql.one(AiSql.call(tenant,"AiDecideApplicationV1",AiTenantApplicationService::approval,
                Objects.requireNonNull(approval),Objects.requireNonNull(expected).bytes(),decision.name(),mask,days,UUID.randomUUID()));
    }
    private static Approval approval(java.sql.ResultSet r) throws java.sql.SQLException {
        String integration = r.getString("IntegrationId");
        return new Approval(AiSql.uuid(r,"ApprovalId"),AiSql.uuid(r,"ApplicationId"),r.getLong("ApplicationRevision"),
                integration == null ? null : UUID.fromString(integration),r.getString("State"),
                AiScope.fromMask(r.getInt("ScopeMask")),AiSql.instant(r,"ExpiresAt"),new RowVersion(r.getBytes("RowVer")));
    }
}
