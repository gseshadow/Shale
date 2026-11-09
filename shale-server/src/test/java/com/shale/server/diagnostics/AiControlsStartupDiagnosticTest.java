package com.shale.server.diagnostics;

import static com.shale.server.diagnostics.AiStartupControls.Control.*;
import static com.shale.server.diagnostics.AiStartupControls.Value.*;
import static org.junit.jupiter.api.Assertions.*;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.shale.server.diagnostics.AiStartupControls.Control;
import com.shale.server.diagnostics.AiStartupControls.Value;
import com.shale.server.mcp.McpTransportConfiguration;
import com.shale.server.mcp.McpTransportProperties;
import com.shale.server.oauth.McpOAuthSecurityConfiguration;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.AnnotatedBeanDefinitionReader;
import org.springframework.boot.autoconfigure.condition.ConditionEvaluationReport;
import org.springframework.boot.info.BuildProperties;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockServletContext;
import org.springframework.web.context.support.GenericWebApplicationContext;

class AiControlsStartupDiagnosticTest {
    private static final String TIME = "2026-11-09T12:00:00Z";
    private static final String ID = "01234567-89ab-cdef-0123-456789abcdef";

    @ParameterizedTest @EnumSource(Control.class)
    void eachAllowlistedControlResolvesTrueFalseAndUnknownWithoutFalseDefaults(Control control) {
        var env = new MockEnvironment();
        assertEquals(UNKNOWN, AiStartupControls.read(env, control).get(control), control.key());
        for (String raw : new String[]{"true", "TRUE", "false", "FALSE"}) {
            env.setProperty(control.key(), raw);
            assertEquals(Value.of(Boolean.parseBoolean(raw)), AiStartupControls.read(env, control).get(control), control.key());
        }
        for (String raw : new String[]{"", "no", "off", "0", " false ", "${unresolved}", "credential-sentinel\nFALSE"}) {
            env.setProperty(control.key(), raw);
            assertEquals(UNKNOWN, AiStartupControls.read(env, control).get(control),
                    control.key() + " must never label absent/nonliteral evidence FALSE");
        }
    }

    @ParameterizedTest @EnumSource(Control.class)
    void missingUnknownOrDisagreeingConsumerEvidenceCannotBecomeFalse(Control control) {
        for (Value other : Value.values()) {
            var values = all(FALSE);
            AiControlsStartupDiagnostic.agree(values, Map.of(control, other), control);
            assertEquals(other == FALSE ? FALSE : UNKNOWN, values.get(control), control.key());
        }
        var values = all(FALSE);
        AiControlsStartupDiagnostic.agree(values, Map.of(), control);
        assertEquals(UNKNOWN, values.get(control));
        AiControlsStartupDiagnostic.agree(values, Map.of(control, FALSE), control);
        assertEquals(UNKNOWN, values.get(control), "Later FALSE must not erase uncertainty");
    }

    @Test void fixedFormatReportsEveryIndividualValueAndBlocksEveryNonOffCombination() {
        for (int mask = 0; mask < 256; mask++) {
            var values = all(FALSE);
            StringBuilder expected = new StringBuilder("SHALE_AI_CONTROLS_STARTUP");
            for (Control control : Control.values()) {
                Value value = Value.of((mask & (1 << control.ordinal())) != 0);
                values.put(control, value);
                expected.append(' ').append(control.key()).append('=').append(value);
            }
            expected.append(" replicaId=replica_1 buildVersion=1.0.138 buildTime=").append(TIME)
                    .append(" startedAt=").append(TIME).append(" startupId=").append(ID)
                    .append(" readiness=").append(mask == 0 ? "READY" : "BLOCKED");
            assertEquals(expected.toString(), format(values), "Every independent control must survive formatting: " + mask);
        }
        for (Control control : Control.values()) {
            var values = all(FALSE);
            values.put(control, UNKNOWN);
            assertTrue(format(values).contains(control.key() + "=UNKNOWN"));
            assertTrue(format(values).endsWith("readiness=BLOCKED"));
            values.remove(control);
            assertTrue(format(values).contains(control.key() + "=UNKNOWN"));
        }
    }

    @Test void metadataIsBoundedAndUnsafeOrMissingCorrelationBlocksAcceptance() {
        for (String value : new String[]{null, "", "a".repeat(129), "secret-sentinel\nFALSE", "https://user:pass@host", "a=b", "\u001b[31m"}) {
            String line = AiControlsStartupDiagnostic.format(all(FALSE), value, value, value, value, value);
            assertTrue(line.endsWith("replicaId=unavailable buildVersion=unavailable buildTime=unavailable"
                    + " startedAt=unavailable startupId=unavailable readiness=BLOCKED"));
            assertFalse(line.contains("secret-sentinel"));
            assertFalse(line.contains("\n"));
        }
        assertTrue(AiControlsStartupDiagnostic.format(all(FALSE), "replica", "1.0.138", "not-a-time", TIME, ID)
                .endsWith("readiness=BLOCKED"));
    }

    @Test void actualOffCompositionReconcilesPropertiesGatesConditionsAndRegistrations() {
        try (var context = context(Map.of())) {
            assertEquals(all(FALSE), new AiControlsStartupDiagnostic(context).observe(),
                    "Real OFF Spring composition must establish every control without SQL or AI credentials");
        }
    }

    @Test void springEnvironmentAliasesAndHigherPriorityOverridesUseTheCanonicalControlNames() {
        var env = new MockEnvironment();
        Map<String, Object> aliases = new HashMap<>();
        for (Control control : Control.values()) aliases.put(control.key().replace(".", "_").replace("-", "").toUpperCase(java.util.Locale.ROOT), "true");
        env.getPropertySources().addLast(new org.springframework.core.env.SystemEnvironmentPropertySource(
                org.springframework.core.env.StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, aliases));
        org.springframework.boot.context.properties.source.ConfigurationPropertySources.attach(env);
        assertEquals(all(TRUE), AiStartupControls.read(env, Control.values()),
                "Spring relaxed environment binding must resolve every canonical control, including hyphen removal");
        Map<String, Object> overrides = new HashMap<>();
        for (Control control : Control.values()) overrides.put(control.key(), "false");
        env.getPropertySources().addFirst(new MapPropertySource("higher-priority", overrides));
        assertEquals(all(FALSE), AiStartupControls.read(env, Control.values()),
                "Higher-priority resolved values win over lower-priority environment settings");
    }

    @Test void noncanonicalConditionExpressionCannotBeMistakenForConsistentFalse() {
        // Boolean conversion accepts uppercase TRUE; the existing expression intentionally compares
        // lowercase literals. Parents remain OFF so this never constructs an AI boundary.
        try (var context = context(Map.of(CASE_DATA.key(), "TRUE"))) {
            var observed = new AiControlsStartupDiagnostic(context).observe();
            assertEquals(UNKNOWN, observed.get(CASE_DATA),
                    "Disagreement between Boolean policy and the actual expression must fail closed");
            assertFalse(context.containsBean("mcpAiCaseData"), "This diagnostic fault test must not create an AI boundary");
        }
    }

    @Test void enabledChildAndGlobalAreReportedIndividuallyEvenWhenNoHandlerCanActivate() {
        for (Control control : new Control[]{AI, SEARCH, OVERVIEW}) {
            try (var context = context(Map.of(control.key(), "true"))) {
                var expected = all(FALSE);
                expected.put(control, TRUE);
                assertEquals(expected, new AiControlsStartupDiagnostic(context).observe(),
                        "Absent child beans must not disguise an independently TRUE control: " + control.key());
                assertFalse(context.containsBean("mcpServlet"));
                assertFalse(context.containsBean("mcpSearchHandler"));
                assertFalse(context.containsBean("mcpOverviewHandler"));
            }
        }
    }

    @Test void resolvedPrecedenceAndPlaceholdersAreComparedWithActualStartupConsumption() {
        try (var context = context(Map.of(SEARCH.key(), "${diagnostic.test-switch:false}",
                "diagnostic.test-switch", "true"))) {
            var diagnostic = new AiControlsStartupDiagnostic(context);
            assertEquals(TRUE, diagnostic.observe().get(SEARCH), "Resolve placeholders before reporting");
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("late-override",
                    Map.of(SEARCH.key(), "false")));
            assertEquals(UNKNOWN, diagnostic.observe().get(SEARCH),
                    "A later override must not overwrite the TRUE value consumed during startup");
        }
    }

    @Test void mismatchedBoundRecordCannotOverrideTheGateThatActuallyStarted() {
        try (var context = context(Map.of())) {
            var factory = context.getDefaultListableBeanFactory();
            String name = context.getBeanNamesForType(McpTransportProperties.class)[0];
            factory.destroySingleton(name);
            factory.registerSingleton(name, new McpTransportProperties(true, null));
            assertEquals(UNKNOWN, new AiControlsStartupDiagnostic(context).observe().get(MCP),
                    "Bound configuration and constructed gate must agree");
        }
    }

    @Test void actualOAuthGateObservationsPreserveCompositionAuthorizationAndExchangeIndependently() {
        for (int mask = 0; mask < 4; mask++) {
            boolean authorization = (mask & 1) != 0, exchange = (mask & 2) != 0;
            var tokens = org.mockito.Mockito.mock(com.shale.server.runtime.ShaleAuthTokenService.class);
            var auth = org.mockito.Mockito.mock(com.shale.server.runtime.ServerAuthSessionService.class);
            var sessions = org.mockito.Mockito.mock(com.shale.server.runtime.DurableSessionStore.class);
            var connections = org.mockito.Mockito.mock(com.shale.server.runtime.RuntimeConnectionProvider.class);
            try (var context = context(Map.of(OAUTH_COMPOSITION.key(), "true",
                    OAUTH_AUTHORIZATION.key(), Boolean.toString(authorization),
                    OAUTH_EXCHANGE.key(), Boolean.toString(exchange),
                    "shale.ai.oauth.public-origin", "https://shale.example"), candidate -> {
                candidate.getBeanFactory().registerSingleton("tokens", tokens);
                candidate.getBeanFactory().registerSingleton("auth", auth);
                candidate.getBeanFactory().registerSingleton("sessions", sessions);
                candidate.getBeanFactory().registerSingleton("connections", connections);
                if (exchange) candidate.addBeanFactoryPostProcessor(factory -> factory.registerSingleton("oauthCodeBootstrap",
                        (com.shale.core.runtime.DbSessionProvider) () -> { throw new AssertionError("Diagnostic opened SQL"); }));
            })) {
                var expected = all(FALSE);
                expected.put(OAUTH_COMPOSITION, TRUE);
                expected.put(OAUTH_AUTHORIZATION, Value.of(authorization));
                expected.put(OAUTH_EXCHANGE, Value.of(exchange));
                assertEquals(expected, new AiControlsStartupDiagnostic(context).observe(),
                        "The initialized OAuth security gate and exchange condition must agree for mask " + mask);
                org.mockito.Mockito.verifyNoInteractions(tokens, auth, sessions, connections);
            }
        }
    }

    @Test void unexpectedOrUninitializedConditionalBeanBlocksInsteadOfBeingInstantiated() {
        try (var context = context(Map.of())) {
            context.registerBean("mcpServlet", Object.class, () -> { throw new AssertionError("Diagnostic activated a bean"); },
                    definition -> definition.setLazyInit(true));
            assertEquals(UNKNOWN, new AiControlsStartupDiagnostic(context).observe().get(MCP),
                    "A lazy unexpected registration is not proof of absence or permission to initialize it");
        }
        try (var context = context(Map.of())) {
            context.getBeanFactory().registerSingleton("oauthCodeBootstrap", new Object());
            assertEquals(UNKNOWN, new AiControlsStartupDiagnostic(context).observe().get(OAUTH_EXCHANGE));
        }
    }

    @Test void missingOrInconsistentConditionDecisionsAreNotOffEvidence() {
        try (var context = context(Map.of())) {
            var report = ConditionEvaluationReport.get(context.getBeanFactory());
            var wrong = report.getConditionAndOutcomesBySource().get(
                    "com.shale.server.mcp.McpTransportConfiguration#mcpServlet").iterator().next();
            report.recordConditionEvaluation("com.shale.server.mcp.McpTransportConfiguration#mcpServlet",
                    wrong.getCondition(), org.springframework.boot.autoconfigure.condition.ConditionOutcome.match());
            assertEquals(UNKNOWN, new AiControlsStartupDiagnostic(context).observe().get(MCP),
                    "Contradictory recorded decisions must not produce FALSE");
        }
        try (var empty = new GenericWebApplicationContext()) {
            empty.setEnvironment(new MockEnvironment());
            empty.refresh();
            assertEquals(all(UNKNOWN), new AiControlsStartupDiagnostic(empty).observe(),
                    "Missing consumers/conditions must never be inferred from an empty context");
        }
    }

    @Test void emitsExactlyOneInfoEntryWithNoExceptionsOrUnrelatedSettings() {
        var logger = (Logger) LoggerFactory.getLogger(AiControlsStartupDiagnostic.class);
        Level previous = logger.getLevel();
        var appender = new ListAppender<ILoggingEvent>();
        appender.start(); logger.addAppender(appender); logger.setLevel(Level.INFO);
        try (var context = context(Map.of("unrelated.password", "private-sentinel", AI.key(), "false"))) {
            Properties build = new Properties();
            build.setProperty("version", "1.0.138"); build.setProperty("time", TIME);
            context.getBeanFactory().registerSingleton("buildProperties", new BuildProperties(build));
            var diagnostic = new AiControlsStartupDiagnostic(context);
            diagnostic.run(null); diagnostic.run(null);
            assertEquals(1, appender.list.size(), "Replacement JVMs get new evidence; repeated runner calls do not");
            var event = appender.list.getFirst();
            assertEquals(Level.INFO, event.getLevel());
            assertNull(event.getThrowableProxy());
            String line = event.getFormattedMessage();
            for (Control control : Control.values()) assertTrue(line.contains(" " + control.key() + "=FALSE"));
            assertFalse(line.contains("private-sentinel"));
            assertFalse(line.contains("unrelated.password"));
            assertTrue(line.contains(" buildVersion=1.0.138 buildTime=" + TIME));
            assertTrue(line.matches(".* startupId=[a-f0-9-]{36} readiness=(READY|BLOCKED)"));

            var failed = org.mockito.Mockito.mock(org.springframework.context.ConfigurableApplicationContext.class);
            org.mockito.Mockito.when(failed.getEnvironment()).thenThrow(new IllegalStateException("private-sentinel"));
            assertDoesNotThrow(() -> new AiControlsStartupDiagnostic(failed).run(null),
                    "Diagnostic failure must block evidence acceptance without an ordinary startup outage");
            var failureEvent = appender.list.get(1);
            for (Control control : Control.values()) assertTrue(failureEvent.getFormattedMessage().contains(" " + control.key() + "=UNKNOWN"));
            assertTrue(failureEvent.getFormattedMessage().endsWith("readiness=BLOCKED"));
            assertFalse(failureEvent.getFormattedMessage().contains("private-sentinel"));
            assertNull(failureEvent.getThrowableProxy());
        } finally {
            logger.detachAppender(appender); appender.stop(); logger.setLevel(previous);
        }
    }

    private static GenericWebApplicationContext context(Map<String, String> overrides) {
        return context(overrides, ignored -> { });
    }

    private static GenericWebApplicationContext context(Map<String, String> overrides,
            java.util.function.Consumer<GenericWebApplicationContext> beforeRefresh) {
        var context = new GenericWebApplicationContext();
        var env = new MockEnvironment(); env.setActiveProfiles("azure");
        Map<String, Object> defaults = new HashMap<>();
        for (Control control : Control.values()) defaults.put(control.key(), "false");
        env.getPropertySources().addLast(new MapPropertySource("defaults", defaults));
        env.getPropertySources().addFirst(new MapPropertySource("overrides", new HashMap<>(overrides)));
        context.setEnvironment(env); context.setServletContext(new MockServletContext());
        new AnnotatedBeanDefinitionReader(context).register(McpOAuthSecurityConfiguration.class, McpTransportConfiguration.class);
        beforeRefresh.accept(context);
        try { context.refresh(); return context; }
        catch (RuntimeException failure) { context.close(); throw failure; }
    }

    private static EnumMap<Control, Value> all(Value value) {
        var values = new EnumMap<Control, Value>(Control.class);
        for (Control control : Control.values()) values.put(control, value);
        return values;
    }

    private static String format(Map<Control, Value> values) {
        return AiControlsStartupDiagnostic.format(values, "replica_1", "1.0.138", TIME, TIME, ID);
    }
}
