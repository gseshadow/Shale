package com.shale.server.mcp;

import com.shale.server.oauth.McpOAuthProperties;
import com.shale.server.diagnostics.AiStartupControls;
import static com.shale.server.diagnostics.AiStartupControls.Control.*;
import com.shale.server.diagnostics.AiStartupControls.Control;
import com.shale.server.diagnostics.AiStartupControls.Value;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

/** One policy for SQL readiness, transport and both Case handlers. Configuration grants no SQL authority. */
final class McpCaseCompositionPolicy {
    static final String COMPOSITION = "shale.ai.mcp.case-data-composition-enabled";
    private final Environment environment;
    private final McpOAuthProperties identity;
    private final Map<Control, Value> startupControls;

    McpCaseCompositionPolicy(Environment environment, McpOAuthProperties identity) {
        this.environment = environment;
        this.identity = identity;
        this.startupControls = new ConcurrentHashMap<>(AiStartupControls.read(environment,
                AI, MCP, CASE_DATA, SEARCH, OVERVIEW));
        if (production() && environment.acceptsProfiles(Profiles.of("test", "local", "dev"))) throw refused();
    }

    boolean production() {
        return environment.acceptsProfiles(Profiles.of("azure", "prod"));
    }

    boolean readinessEnabled() { return flag(COMPOSITION); }

    boolean boundaryRequired() {
        return readinessEnabled() || flag("shale.ai.enabled") && flag("shale.ai.mcp.enabled")
                && (flag("shale.ai.mcp.search-cases-enabled") || flag("shale.ai.mcp.get-case-overview-enabled"));
    }

    void requireBoundary() {
        if (readinessEnabled()) {
            requireProductionIdentity();
        } else if (production() || !environment.acceptsProfiles(Profiles.of("test"))
                || identity.identity() != McpOAuthProperties.Identity.TEST
                || environment.acceptsProfiles(Profiles.of("local", "dev"))) {
            throw refused();
        }
    }

    void requireHandler(String flag) {
        requireBoundary();
        if (!flag("shale.ai.enabled") || !flag("shale.ai.mcp.enabled") || !flag(flag)) throw refused();
    }

    void requireTransport() {
        if (!flag("shale.ai.mcp.enabled")) return;
        // The isolated test transport retains its independent global-OFF denial behavior.
        if (production() || identity.identity() == McpOAuthProperties.Identity.PRODUCTION) {
            requireProductionIdentity();
            if (!readinessEnabled() || !flag("shale.ai.enabled")) throw refused();
        }
    }

    private void requireProductionIdentity() {
        if (!production() || identity.identity() != McpOAuthProperties.Identity.PRODUCTION
                || environment.acceptsProfiles(Profiles.of("test", "local", "dev"))) throw refused();
        // The properties constructor enforces canonical origin syntax and nonlocal production identity.
        if (identity.publicOrigin() == null || !identity.publicOrigin().startsWith("https://")) throw refused();
        identity.issuer();
        identity.resource();
    }

    Map<Control, Value> startupControls() { return Map.copyOf(startupControls); }

    private boolean flag(String name) {
        boolean consumed = environment.getProperty(name, Boolean.class, false);
        // Retain disagreement with any value actually consumed by this policy. No decision changes.
        for (Control control : startupControls.keySet()) {
            if (control.key().equals(name)) startupControls.merge(control, Value.of(consumed), Value::agree);
        }
        return consumed;
    }
    private static IllegalStateException refused() {
        return new IllegalStateException("AI Case composition is not authorized by the active profile, identity and controls.");
    }
}
