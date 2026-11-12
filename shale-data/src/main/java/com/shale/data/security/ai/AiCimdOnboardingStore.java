package com.shale.data.security.ai;

import com.shale.core.runtime.DbSessionProvider;
import com.shale.core.security.ai.AiScope;
import java.util.*;
import static com.shale.data.security.ai.AiIntegrationSecurityService.RowVersion;

/** One exact public application and the authenticated tenant's current approval, never an inventory. */
public final class AiCimdOnboardingStore {
    private final DbSessionProvider db;
    private final String issuer,resource;
    public AiCimdOnboardingStore(DbSessionProvider db,String issuer,String resource) {
        this.db=Objects.requireNonNull(db);this.issuer=issuer;this.resource=resource;
        AiIntegrationSecurityService.configuredHttps(issuer);AiIntegrationSecurityService.configuredHttps(resource);
    }
    public record State(boolean admin,AiApplicationRegistryService.Application application,
                        byte[] digest,int methods,AiTenantApplicationService.Approval approval) {
        public State { digest=digest==null?null:digest.clone(); }
        @Override public byte[] digest(){return digest==null?null:digest.clone();}
        @Override public String toString(){return "CimdOnboardingState[redacted]";}
    }
    public State find(String external,UUID originalWeb) {
        return AiSql.one(AiSql.call(db,"AiCimdGetOnboardingV1",r->{
            String application=r.getString("ApplicationId"),approval=r.getString("ApprovalId");
            return new State(r.getBoolean("IsAdmin"),application==null?null:new AiApplicationRegistryService.Application(
                    UUID.fromString(application),r.getLong("Revision"),r.getBoolean("Disabled"),new RowVersion(r.getBytes("ApplicationVersion"))),
                    r.getBytes("MetadataDigest"),r.getInt("MethodMask"),approval==null?null:new AiTenantApplicationService.Approval(
                    UUID.fromString(approval),UUID.fromString(application),r.getLong("ApplicationRevision"),
                    r.getString("IntegrationId")==null?null:UUID.fromString(r.getString("IntegrationId")),r.getString("State"),
                    AiScope.fromMask(r.getInt("ScopeMask")),AiSql.instant(r,"ExpiresAt"),new RowVersion(r.getBytes("ApprovalVersion"))));
        },external,issuer,resource,Objects.requireNonNull(originalWeb)));
    }
}
