package com.shale.data.security.ai;

import com.shale.core.runtime.DbSessionProvider;
import java.util.Objects;
import java.util.UUID;
import static com.shale.data.security.ai.AiIntegrationSecurityService.*;

/** Restricted global control plane only. No tenant business queries or tenant-admin authority. */
public final class AiProductCapabilityService {
    private final DbSessionProvider operators;
    public AiProductCapabilityService(DbSessionProvider operators) { this.operators = Objects.requireNonNull(operators); }
    public Policy inspect() {
        return AiSql.one(AiSql.call(operators, "AiGetProductCapabilityV1", r -> new Policy(
                r.getBoolean("Enabled"), new RowVersion(r.getBytes("RowVer")))));
    }
    public Policy setEnabled(boolean enabled, RowVersion expected) {
        return AiSql.one(AiSql.call(operators, "AiSetProductCapabilityV1", r -> new Policy(
                r.getBoolean("Enabled"), new RowVersion(r.getBytes("RowVer"))), enabled,
                Objects.requireNonNull(expected).bytes(), UUID.randomUUID()));
    }
}
