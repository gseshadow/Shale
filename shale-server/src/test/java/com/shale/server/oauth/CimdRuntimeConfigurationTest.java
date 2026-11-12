package com.shale.server.oauth;

import com.microsoft.sqlserver.jdbc.SQLServerDataSource;
import com.shale.data.security.ai.*;
import com.shale.core.security.ai.AiScope;
import java.sql.*;
import java.util.*;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mock.env.MockEnvironment;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Provider/configuration tests only. Scripted JDBC is not native Azure or SQL attestation evidence. */
class CimdRuntimeConfigurationTest {
    static final McpOAuthProperties IDENTITY = new McpOAuthProperties("https://shale.example",
            McpOAuthProperties.Identity.PRODUCTION, true);
    final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(CimdRuntimeConfiguration.class, CimdConnectionConfiguration.class, DcrConnectionConfiguration.class);

    static MockEnvironment eligible() {
        var env = new MockEnvironment();
        env.setActiveProfiles("azure");
        for (String key : List.of("shale.ai.cimd.enabled", "shale.ai.dcr.enabled", "shale.ai.enabled",
                "shale.ai.mcp.enabled", "shale.ai.mcp.case-data-composition-enabled",
                "shale.ai.oauth.composition-enabled", "shale.ai.oauth.authorization-enabled", "shale.ai.oauth.exchange-enabled"))
            env.setProperty(key, "true");
        env.setProperty("shale.ai.oauth.identity", "PRODUCTION");
        env.setProperty("shale.ai.oauth.public-origin", IDENTITY.publicOrigin());
        env.setProperty("shale.ai.oauth.browser-origin", "https://web.shale.example");
        env.setProperty("shale.ai.data.contract-version", "V2");
        env.setProperty("shale.ai.cimd.contract-version", "DCR_V3");
        env.setProperty("shale.ai.data.database", "Synthetic");
        for (String name : CimdRuntimeConnections.roles(env).keySet()) settings(env, "shale.ai.cimd.runtime." + name, name);
        settings(env, "shale.ai.data.verifier", "predecessor-reviewer");
        settings(env, "shale.ai.oauth.bootstrap", "bootstrap");
        env.setProperty("shale.ai.data.jdbc-url", "jdbc:sqlserver://sql.example;databaseName=Synthetic");
        env.setProperty("shale.ai.data.username", "predecessor");
        env.setProperty("shale.ai.data.password", "synthetic-predecessor");
        return env;
    }

    static void settings(MockEnvironment env, String prefix, String name) {
        env.setProperty(prefix + ".jdbc-url", "jdbc:sqlserver://sql.example;databaseName=Synthetic");
        env.setProperty(prefix + ".username", name);
        env.setProperty(prefix + ".password", "synthetic-" + name);
    }

    @Test void offProductionDoesNotResolveSqlOrCapabilities() {
        for (String profile : List.of("azure", "prod")) runner.withInitializer(c -> c.getEnvironment().setActiveProfiles(profile))
                .run(c -> {
                    assertNull(c.getStartupFailure());
                    assertFalse(c.containsBean("cimdRuntimeConnections"));
                    assertFalse(c.containsBean("cimdServices"));
                    assertFalse(c.containsBean("cimdCaseConnections"));
                    assertFalse(c.containsBean("dcrCapabilities"));
                });
    }

    @Test void everyMissingDedicatedSettingRefusesBeforeResolvingAnyDatasource() {
        var complete = eligible();
        var keys = new ArrayList<String>();
        for (String name : CimdRuntimeConnections.roles(complete).keySet())
            for (String suffix : List.of("jdbc-url", "username", "password")) keys.add("shale.ai.cimd.runtime." + name + "." + suffix);
        for (String prefix : List.of("shale.ai.data.verifier", "shale.ai.oauth.bootstrap"))
            for (String suffix : List.of("jdbc-url", "username", "password")) keys.add(prefix + "." + suffix);
        for (String missing : keys) {
            var env = eligible(); env.setProperty(missing, "");
            runner.withInitializer(c -> c.setEnvironment(env)).run(c -> {
                assertNotNull(c.getStartupFailure(), missing);
                assertTrue(c.getStartupFailure().getMessage().contains(missing), "Must reject the missing setting before SQL/bean resolution: " + missing);
            });
        }
    }

    @Test void productionEligibilityRejectsMixedProfilesWrongIdentitiesContractsAndPartialControls() {
        for (String key : List.of("shale.ai.mcp.case-data-composition-enabled", "shale.ai.oauth.exchange-enabled")) {
            var env = eligible(); env.setProperty(key, "false");
            assertThrows(IllegalStateException.class, () -> CimdRuntimeEligibility.require(env, true), key);
        }
        for (var entry : Map.of("shale.ai.data.contract-version", "V1", "shale.ai.cimd.contract-version", "EXTERNAL_V2",
                "shale.ai.oauth.identity", "TEST").entrySet()) {
            var env = eligible(); env.setProperty(entry.getKey(), entry.getValue());
            assertThrows(IllegalStateException.class, () -> CimdRuntimeEligibility.require(env, true));
        }
        var mixed = eligible(); mixed.setActiveProfiles("azure", "test");
        assertThrows(IllegalStateException.class, () -> CimdRuntimeEligibility.require(mixed, true));
        var missing = eligible(); missing.setProperty("shale.ai.cimd.contract-version", "");
        assertThrows(IllegalStateException.class, () -> CimdRuntimeEligibility.require(missing, true));
        for (String origin : List.of("http://shale.example", "https://shale.test", "https://shale.example/")) {
            var env = eligible(); env.setProperty("shale.ai.oauth.public-origin", origin);
            assertThrows(IllegalArgumentException.class, () -> CimdRuntimeEligibility.require(env, true));
        }
        assertDoesNotThrow(() -> CimdRuntimeEligibility.require(eligible(), true));
    }

    @Test void protectedUrlOptionsAndWrongDatabaseAreRejectedWithoutOpeningSql() {
        for (String url : List.of("jdbc:sqlserver://sql.example;databaseName=Synthetic;trustServerCertificate=true",
                "jdbc:sqlserver://sql.example;databaseName=Other", "jdbc:sqlserver://sql.example")) {
            var env = eligible(); env.setProperty("shale.ai.cimd.runtime.browser.jdbc-url", url);
            assertThrows(IllegalStateException.class, () -> new CimdRuntimeConnections(env));
        }
        var source = (SQLServerDataSource) CimdRuntimeConnections.source(eligible(), "shale.ai.cimd.runtime.browser");
        assertEquals("true", source.getEncrypt());
        assertFalse(source.getTrustServerCertificate());
        assertEquals(2, source.getLoginTimeout());
        assertEquals(5000, source.getSocketTimeout());
    }

    @Test void v3SelectionDoesNotDependOnPublicRegistrationBeingEnabled() {
        var env = eligible(); env.setProperty("shale.ai.dcr.enabled", "false");
        assertEquals(AiExternalContract.DCR_V3, CimdRuntimeEligibility.contract(env));
        assertFalse(CimdRuntimeConnections.roles(env).containsKey("dcr-endpoint"));
        assertDoesNotThrow(() -> CimdRuntimeEligibility.require(env, false));
        var manual = new MockEnvironment(); manual.setActiveProfiles("azure");
        assertEquals(AiExternalContract.CIMD_V1, CimdRuntimeEligibility.contract(manual), "Retain existing manual/readiness selection");
    }

    static final class ScriptedSource {
        final DataSource source = mock(DataSource.class);
        final List<String> calls = new ArrayList<>();
        final Map<Integer, Integer> context = new HashMap<>();
        final List<Connection> opened = new ArrayList<>();
        String role, database = "Synthetic", server = "sql.example", sid, user;
        int authentication = 2, sessionProof = 1;
        ScriptedSource(String name, String role) throws Exception {
            this.role = role; sid = name; user = name;
            when(source.getConnection()).thenAnswer(invocation -> connection());
        }
        Connection connection() throws Exception {
            var c = mock(Connection.class); opened.add(c); when(c.getAutoCommit()).thenReturn(true);
            when(c.createStatement()).thenAnswer(invocation -> {
                var s = mock(Statement.class);
                when(s.executeQuery(anyString())).thenAnswer(query -> {
                    String sql = query.getArgument(0); var r = mock(ResultSet.class);
                    if (sql.equals("SELECT name FROM sys.user_token")) {
                        when(r.next()).thenReturn(true, true, true, false);
                        when(r.getString(1)).thenReturn(user, "public", role);
                    } else {
                        when(r.next()).thenReturn(true, false);
                        when(r.getString(1)).thenReturn(database); when(r.getString(2)).thenReturn(server);
                        when(r.getBytes(3)).thenReturn(sid.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                        when(r.getString(4)).thenReturn(user); when(r.getInt(5)).thenReturn(10);
                        when(r.getString(6)).thenReturn(user); when(r.getString(7)).thenReturn("S");
                        when(r.getInt(8)).thenReturn(authentication); when(r.getInt(9)).thenReturn(5);
                        when(r.getInt(14)).thenReturn(sessionProof);
                    }
                    return r;
                }); return s;
            });
            when(c.prepareStatement(anyString())).thenAnswer(invocation -> {
                String sql = invocation.getArgument(0); var p = mock(PreparedStatement.class);
                doAnswer(set -> { context.put(sql.contains("ShaleClientId") ? 1 : 2, set.getArgument(1)); return null; })
                        .when(p).setInt(eq(1), anyInt()); return p;
            });
            when(c.prepareCall(anyString())).thenAnswer(invocation -> {
                calls.add(invocation.getArgument(0)); var p = mock(CallableStatement.class);
                when(p.execute()).thenThrow(new SQLException("Synthetic stop after provider dispatch")); return p;
            });
            return c;
        }
    }

    Map<String, ScriptedSource> scripted() throws Exception {
        var result = new LinkedHashMap<String, ScriptedSource>();
        for (var entry : CimdRuntimeConnections.roles(eligible()).entrySet()) result.put(entry.getKey(), new ScriptedSource(entry.getKey(), entry.getValue()));
        return result;
    }
    CimdRuntimeConnections connections(Map<String, ScriptedSource> scripted) {
        var map = new HashMap<String, DataSource>(); scripted.forEach((name, source) -> map.put(name, source.source));
        return new CimdRuntimeConnections(map, "Synthetic");
    }
    static void dispatched(ScriptedSource source, String procedure, org.junit.jupiter.api.function.Executable action) {
        assertThrows(IllegalStateException.class, action);
        assertTrue(source.calls.getLast().startsWith("{call dbo." + procedure + "("), source.calls.toString());
    }

    @Test void validProvidersUseTheirDedicatedIdentityAndAuthenticatedOwnerWithV3Delegation() throws Exception {
        var scripted = scripted(); var c = connections(scripted);
        var config = new CimdRuntimeConfiguration();
        var services = config.cimdServices(c, IDENTITY); var dcr = config.dcrCapabilities(c, IDENTITY);
        var owner = new AiOAuthExchangeStore.Owner(7, 71); var web = UUID.randomUUID();
        String external = "https://client.example/metadata.json";
        dispatched(scripted.get("browser"), "AiExternalFindOAuthIntentV2", () -> services.browser().apply(owner).findIntent("A".repeat(43), web));
        dispatched(scripted.get("issuer"), "AiExternalRefreshV2", () -> services.issuer().apply(owner).refresh("A".repeat(43), external, null, null, null, null));
        dispatched(scripted.get("metadata"), "AiExternalResolvePublicationV2", () -> services.metadata().resolvePublication(external, new byte[32], 1));
        dispatched(scripted.get("resolver"), "AiExternalResolveCodeV2", () -> services.resolver().resolve("A".repeat(43), external, "CODE"));
        dispatched(scripted.get("neutral"), "AiExternalRevokeUnknownV2", () -> services.neutral().revokeUnknown(external, new byte[32], "none", null, null, null, null));
        dispatched(scripted.get("onboarding"), "AiExternalGetOnboardingV2", () -> services.onboarding().apply(owner).find(external, web));
        dispatched(scripted.get("approval"), "AiRequestApplicationV1", () -> services.approvals().apply(owner).request(UUID.randomUUID(), new AiIntegrationSecurityService.RowVersion(new byte[8]), AiScope.fromMask(1)));
        dispatched(scripted.get("publisher"), "AiPublishApplicationV1", () -> services.publisher().publishCimd(external, new byte[32], 1, false, null));
        dispatched(scripted.get("dcr-endpoint"), "AiDcrStatusV3", () -> dcr.admission().enabled());
        dispatched(scripted.get("dcr-endpoint"), "AiDcrRegisterApplicationV3", () -> dcr.registrar().register("Synthetic", "[\"http://127.0.0.1:8081/callback\"]", "[]", 1, 3));
        dispatched(scripted.get("dcr-metadata"), "AiDcrPrepareMetadataV2", () -> dcr.metadata().prepare(new DcrClientIdentifier("dcr." + "a".repeat(64))));
        for (String name : List.of("browser", "issuer", "onboarding", "approval")) assertEquals(Map.of(1, 7, 2, 71), scripted.get(name).context);
        for (String name : List.of("metadata", "resolver", "neutral", "publisher", "dcr-endpoint", "dcr-metadata")) assertTrue(scripted.get(name).context.isEmpty());
        assertEquals(AiExternalContract.DCR_V3, services.browser().apply(owner).contract());
        assertEquals(AiExternalContract.DCR_V3, services.issuer().apply(owner).contract());
        assertEquals(AiExternalContract.DCR_V3, dcr.registrar().contract());
        for (var source : scripted.values()) for (var opened : source.opened) verify(opened).close();
        var caseConnections = config.cimdCaseConnections(c);
        assertThrows(SQLTimeoutException.class, () -> caseConnections.data().apply(System.nanoTime()-1).getConnection());
        try (var data = caseConnections.data().apply(System.nanoTime()+1_000_000_000L).getConnection();
             var review = caseConnections.reviewer().apply(System.nanoTime()+1_000_000_000L).getConnection()) {
            assertFalse(scripted.get("case").opened.isEmpty()); assertFalse(scripted.get("reviewer").opened.isEmpty());
        }
    }

    @Test void actualPrincipalChecksRejectRoleSubstitutionSidReuseWrongDestinationAndAuthentication() throws Exception {
        var scripted = scripted(); var c = connections(scripted);
        var forbidden = new ScriptedSource("predecessor", "unused");
        var roles = CimdRuntimeConnections.roles(eligible());
        assertDoesNotThrow(() -> c.verifyIsolation(roles, List.of(forbidden.source)));
        var browser = scripted.get("browser"); browser.role = "AiExternalOAuthIssuerV2";
        assertThrows(IllegalStateException.class, () -> c.verifyIsolation(roles, List.of(forbidden.source)));
        browser.role = roles.get("browser"); browser.sid = "predecessor";
        assertThrows(IllegalStateException.class, () -> c.verifyIsolation(roles, List.of(forbidden.source)));
        browser.sid = "browser"; browser.database = "Other";
        assertThrows(IllegalStateException.class, () -> c.verifyIsolation(roles, List.of(forbidden.source)));
        browser.database = "Synthetic"; browser.server = "other.example";
        assertThrows(IllegalStateException.class, () -> c.verifyIsolation(roles, List.of(forbidden.source)));
        browser.server = "sql.example"; browser.authentication = 1;
        assertThrows(IllegalStateException.class, () -> c.verifyIsolation(roles, List.of(forbidden.source)));
        browser.authentication = 2; browser.sessionProof = 0;
        assertThrows(IllegalStateException.class, () -> c.verifyIsolation(roles, List.of(forbidden.source)));
        for (var source : scripted.values()) for (var opened : source.opened) verify(opened).close();
    }
}
