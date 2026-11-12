package com.shale.server.oauth;

import com.shale.data.security.ai.*;
import java.util.Objects;
import java.util.function.Function;

/** Explicit operator composition of PR A capabilities. No pool construction, provisioning or fallback.
 * Tenant factories take only authenticated WEB or digest-resolved owners, never request tenant IDs. */
public record CimdServices(AiApplicationRegistryService publisher,
        Function<AiOAuthExchangeStore.Owner,AiCimdOnboardingStore> onboarding,
        Function<AiOAuthExchangeStore.Owner,AiTenantApplicationService> approvals,
        Function<AiOAuthExchangeStore.Owner,AiCimdOAuthStore> browser,
        Function<AiOAuthExchangeStore.Owner,AiCimdOAuthStore> issuer,
        AiCimdOAuthStore metadata,AiCimdOAuthStore resolver,AiCimdOAuthStore neutral) {
    public CimdServices {
        Objects.requireNonNull(publisher);Objects.requireNonNull(onboarding);Objects.requireNonNull(approvals);
        Objects.requireNonNull(browser);Objects.requireNonNull(issuer);Objects.requireNonNull(metadata);
        Objects.requireNonNull(resolver);Objects.requireNonNull(neutral);
    }
    @Override public String toString(){return "CimdServices[redacted]";}
}
