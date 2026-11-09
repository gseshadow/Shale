package com.shale.server.diagnostics;

import static com.shale.server.diagnostics.AiStartupControls.Control.*;
import static com.shale.server.diagnostics.AiStartupControls.Value.*;

import com.shale.server.diagnostics.AiStartupControls.Control;
import com.shale.server.diagnostics.AiStartupControls.Value;
import com.shale.server.mcp.McpTransportConfiguration;
import com.shale.server.mcp.McpTransportProperties;
import com.shale.server.oauth.McpOAuthProperties;
import com.shale.server.oauth.McpOAuthSecurityConfiguration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionEvaluationReport;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

/** Startup evidence for dormant-release acceptance; no health, feature, SQL or security authority. */
@Component
public final class AiControlsStartupDiagnostic implements ApplicationRunner {
    private static final Logger LOG = LoggerFactory.getLogger(AiControlsStartupDiagnostic.class);
    private static final Control[] MCP_CONTROLS = {AI, MCP, CASE_DATA, SEARCH, OVERVIEW};
    private static final Control[] OAUTH_CONTROLS = {OAUTH_COMPOSITION, OAUTH_AUTHORIZATION, OAUTH_EXCHANGE};
    private static final String MCP_CONFIG = "com.shale.server.mcp.McpTransportConfiguration#";
    private final ConfigurableApplicationContext context;
    private final AtomicBoolean emitted = new AtomicBoolean();
    private final String startupId = UUID.randomUUID().toString();

    public AiControlsStartupDiagnostic(ConfigurableApplicationContext context) { this.context = context; }

    @Override
    public void run(ApplicationArguments ignored) {
        if (!emitted.compareAndSet(false, true)) return;
        Map<Control, Value> values = unknown();
        String version = null, buildTime = null, startedAt = null, replica = null;
        try {
            values = observe();
            var build = existing(BuildProperties.class);
            if (build != null) {
                version = build.getVersion();
                if (build.getTime() != null) buildTime = build.getTime().toString();
            }
            startedAt = Instant.ofEpochMilli(context.getStartupDate()).toString();
            // Only the same nonsecret Azure identifier already permitted by SHALE_JVM_STARTUP.
            replica = System.getenv("WEBSITE_INSTANCE_ID");
        } catch (RuntimeException unavailable) {
            // Missing evidence blocks release acceptance, never ordinary application startup.
            values = unknown();
        }
        try {
            LOG.info(format(values, replica, version, buildTime, startedAt, startupId));
        } catch (RuntimeException unavailable) {
            // A missing/filtered marker is itself an operator readiness blocker. Never log exceptions.
        }
    }

    Map<Control, Value> observe() {
        var values = new EnumMap<Control, Value>(AiStartupControls.read(context.getEnvironment(), Control.values()));
        var transport = existing(McpTransportConfiguration.class);
        var oauth = existing(McpOAuthSecurityConfiguration.class);
        agree(values, transport == null ? Map.of() : transport.startupPolicyControls(), MCP_CONTROLS);
        agree(values, transport == null ? Map.of() : transport.startupGateControls(), AI, MCP);
        agree(values, oauth == null ? Map.of() : oauth.startupControls(), OAUTH_CONTROLS);

        // Reconcile the actual bound records as well as the immutable values used to build the gates.
        var transportProperties = existing(McpTransportProperties.class);
        var oauthProperties = existing(McpOAuthProperties.class);
        agree(values, transportProperties == null ? Map.of() : Map.of(MCP, Value.of(transportProperties.enabled())), MCP);
        agree(values, oauthProperties == null ? Map.of()
                : Map.of(OAUTH_COMPOSITION, Value.of(oauthProperties.compositionEnabled())), OAUTH_COMPOSITION);

        // Inspect existing decisions/registrations only. Never get an AI bean, run readiness/attestation,
        // evaluate a condition again, or log ConditionEvaluationReport (its messages are not allowlisted).
        var expected = Map.copyOf(values);
        var report = ConditionEvaluationReport.get(context.getBeanFactory());
        condition(values, report, MCP_CONFIG + "mcpServlet", "OnPropertyCondition", expected.get(MCP), MCP);
        condition(values, report, MCP_CONFIG + "mcpBodyAdmission", "OnPropertyCondition", expected.get(MCP), MCP);
        Value search = expected.get(AI).and(expected.get(MCP)).and(expected.get(SEARCH));
        Value overview = expected.get(AI).and(expected.get(MCP)).and(expected.get(OVERVIEW));
        condition(values, report, "com.shale.server.mcp.McpSearchConfiguration", "OnPropertyCondition",
                search, AI, MCP, SEARCH);
        condition(values, report, "com.shale.server.mcp.McpOverviewConfiguration", "OnPropertyCondition",
                overview, AI, MCP, OVERVIEW);
        Value boundary = expected.get(CASE_DATA).or(search).or(overview);
        condition(values, report, MCP_CONFIG + "mcpAiCaseData", "OnExpressionCondition", boundary, MCP_CONTROLS);
        condition(values, report, "com.shale.server.oauth.OAuthCodeBootstrapConfiguration#oauthCodeBootstrap",
                "OnPropertyCondition", expected.get(OAUTH_EXCHANGE), OAUTH_EXCHANGE);

        registration(values, expected.get(MCP), initialized("mcpServlet"), MCP);
        registration(values, expected.get(MCP), initialized("mcpBodyAdmission"), MCP);
        registration(values, expected.get(OAUTH_EXCHANGE), initialized("oauthCodeBootstrap"), OAUTH_EXCHANGE);
        var registrations = transport == null ? Map.<Control, Value>of()
                : transport.startupBoundaryRegistrations(context.getBeanFactory());
        registration(values, boundary, registrations.getOrDefault(CASE_DATA, UNKNOWN), MCP_CONTROLS);
        registration(values, search, registrations.getOrDefault(SEARCH, UNKNOWN), AI, MCP, SEARCH);
        registration(values, overview, registrations.getOrDefault(OVERVIEW, UNKNOWN), AI, MCP, OVERVIEW);
        // No legacy authenticator may exist in an all-OFF startup, regardless of its active profile.
        if (expected.get(AI) == FALSE && expected.get(MCP) == FALSE
                && initialized("mcpAccessAuthenticator") != FALSE) invalidate(values, AI, MCP);
        return Map.copyOf(values);
    }

    private <T> T existing(Class<T> type) {
        var factory = context.getBeanFactory();
        String[] names = factory.getBeanNamesForType(type, false, false);
        if (names.length != 1 || !factory.containsSingleton(names[0])) return null;
        return type.cast(factory.getSingleton(names[0]));
    }

    private Value initialized(String name) {
        var factory = context.getBeanFactory();
        if (!factory.containsBean(name)) return FALSE;
        return factory.containsSingleton(name) ? TRUE : UNKNOWN;
    }

    static void agree(Map<Control, Value> values, Map<Control, Value> observed, Control... controls) {
        for (Control control : controls) values.put(control,
                values.getOrDefault(control, UNKNOWN).agree(observed.getOrDefault(control, UNKNOWN)));
    }

    private static void condition(Map<Control, Value> values, ConditionEvaluationReport report,
            String source, String conditionType, Value expected, Control... controls) {
        var outcomes = report.getConditionAndOutcomesBySource().get(source);
        int found = 0;
        boolean consistent = expected != UNKNOWN;
        if (outcomes != null) for (var outcome : outcomes) {
            if (conditionType.equals(outcome.getCondition().getClass().getSimpleName())) {
                found++;
                consistent &= Value.of(outcome.getOutcome().isMatch()) == expected;
            }
        }
        if (found != 1 || !consistent) invalidate(values, controls);
    }

    private static void registration(Map<Control, Value> values, Value expected, Value actual, Control... controls) {
        if (expected == UNKNOWN || actual != expected) invalidate(values, controls);
    }

    private static void invalidate(Map<Control, Value> values, Control... controls) {
        for (Control control : controls) values.put(control, UNKNOWN);
    }

    private static Map<Control, Value> unknown() {
        var values = new EnumMap<Control, Value>(Control.class);
        for (Control control : Control.values()) values.put(control, UNKNOWN);
        return values;
    }

    static String format(Map<Control, Value> values, String replica, String version,
            String buildTime, String startedAt, String startupId) {
        String safeReplica = token(replica, "[A-Za-z0-9_-]{1,128}");
        String safeVersion = token(version, "[A-Za-z0-9_.+-]{1,128}");
        String safeBuildTime = timestamp(buildTime);
        String safeStartedAt = timestamp(startedAt);
        String safeStartupId = token(startupId, "[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}");
        boolean ready = !"unavailable".equals(safeReplica) && !"unavailable".equals(safeVersion)
                && !"unavailable".equals(safeBuildTime) && !"unavailable".equals(safeStartedAt)
                && !"unavailable".equals(safeStartupId);
        var line = new StringBuilder("SHALE_AI_CONTROLS_STARTUP");
        for (Control control : Control.values()) {
            Value value = values.get(control);
            if (value == null) value = UNKNOWN;
            line.append(' ').append(control.key()).append('=').append(value);
            ready &= value == FALSE;
        }
        return line.append(" replicaId=").append(safeReplica).append(" buildVersion=").append(safeVersion)
                .append(" buildTime=").append(safeBuildTime).append(" startedAt=").append(safeStartedAt)
                .append(" startupId=").append(safeStartupId).append(" readiness=").append(ready ? "READY" : "BLOCKED").toString();
    }

    private static String timestamp(String value) {
        if (value == null || value.length() > 40 || !value.matches("[0-9T:.Z-]+")) return "unavailable";
        try { return Instant.parse(value).toString(); }
        catch (RuntimeException invalid) { return "unavailable"; }
    }

    private static String token(String value, String pattern) {
        return value != null && value.length() <= 128 && value.matches(pattern) ? value : "unavailable";
    }
}
