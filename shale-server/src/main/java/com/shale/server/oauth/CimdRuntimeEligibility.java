package com.shale.server.oauth;

import com.shale.data.security.ai.AiExternalContract;
import java.util.List;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

/** Configuration eligibility only. SQL authority still requires the existing live verifiers. */
public final class CimdRuntimeEligibility {
    private CimdRuntimeEligibility() { }

    static boolean production(Environment env) {
        return env.acceptsProfiles(Profiles.of("azure", "prod"));
    }

    static void require(Environment env, boolean dcr) {
        boolean production = production(env);
        boolean development = env.acceptsProfiles(Profiles.of("test", "dev", "local"));
        if (production == development) throw refused();
        for (String flag : List.of("shale.ai.enabled", "shale.ai.mcp.enabled",
                "shale.ai.oauth.composition-enabled", "shale.ai.oauth.authorization-enabled",
                "shale.ai.oauth.exchange-enabled")) {
            if (!env.getProperty(flag, Boolean.class, false))
                throw new IllegalStateException("Complete OAuth/MCP composition required for CIMD.");
        }
        if (dcr && !env.getProperty("shale.ai.cimd.enabled", Boolean.class, false)) throw refused();
        if (!production) {
            String identity = env.getProperty("shale.ai.oauth.identity");
            if (!"DEVELOPMENT".equals(identity) && !"TEST".equals(identity)) throw refused();
            new McpOAuthProperties(env.getRequiredProperty("shale.ai.oauth.public-origin"),
                    McpOAuthProperties.Identity.valueOf(identity), true);
        }
        if (production) {
            if (!env.getProperty("shale.ai.mcp.case-data-composition-enabled", Boolean.class, false)
                    || !"PRODUCTION".equals(env.getProperty("shale.ai.oauth.identity", "PRODUCTION"))
                    || !"V2".equals(env.getProperty("shale.ai.data.contract-version"))
                    || contract(env) != AiExternalContract.DCR_V3) throw refused();
            var identity = new McpOAuthProperties(env.getRequiredProperty("shale.ai.oauth.public-origin"),
                    McpOAuthProperties.Identity.PRODUCTION, true);
            new McpOAuthProperties(env.getRequiredProperty("shale.ai.oauth.browser-origin"), identity.identity(), false);
        }
    }

    /** Production must select the installed successor explicitly, independently of DCR advertisement. */
    public static AiExternalContract contract(Environment env) {
        String value = env.getProperty("shale.ai.cimd.contract-version");
        if (value == null && (!production(env) || !env.getProperty("shale.ai.cimd.enabled", Boolean.class, false)))
            return env.getProperty("shale.ai.dcr.enabled", Boolean.class, false)
                    ? AiExternalContract.DCR_V3 : AiExternalContract.CIMD_V1;
        try { return AiExternalContract.valueOf(value); }
        catch (RuntimeException invalid) { throw refused(); }
    }

    private static IllegalStateException refused() {
        return new IllegalStateException("Explicit compatible profile, identity and verified runtime configuration required.");
    }
}
