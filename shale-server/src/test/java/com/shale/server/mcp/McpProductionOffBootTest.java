package com.shale.server.mcp;

import com.shale.data.config.DataSources;
import com.shale.server.ShaleServerApplication;
import com.shale.server.runtime.*;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;

/** Full Boot/embedded HTTP startup, real ordinary beans, no SQL server or simulated AI attestation. */
@org.junit.jupiter.api.extension.ExtendWith(org.springframework.boot.test.system.OutputCaptureExtension.class)
class McpProductionOffBootTest {
    private final Map<String,String> previous=new LinkedHashMap<>();

    @BeforeEach void isolatedOrdinaryConfiguration() {
        // Fail before Boot starts if an inherited destination could contact a real database.
        for(String key:List.of("SHALE_APP_DB_URL","SHALE_APP_JDBC_URL","SHALE_APP_DB_USER","SHALE_APP_USER",
                "SHALE_APP_DB_PASSWORD","SHALE_APP_PASS","SHALE_RT_DB_URL","SHALE_RT_JDBC_URL",
                "SHALE_RT_DB_USER","SHALE_RT_USER","SHALE_RT_DB_PASSWORD","SHALE_RT_PASS",
                "SHALE_AI_DATA_JDBC_URL","SHALE_AI_DATA_USERNAME","SHALE_AI_DATA_PASSWORD",
                "SHALE_AUTH_TOKEN_SECRET","SHALE_AUTH_SESSION_BINDING_CUTOVER_AT"))
            assertNull(System.getenv(key),"Isolated Boot verification must not inherit "+key);
        Map<String,String> settings=Map.of(
                "SHALE_APP_DB_URL","jdbc:sqlserver://127.0.0.1:9;databaseName=BootApp;loginTimeout=1",
                "SHALE_APP_DB_USER","synthetic-app","SHALE_APP_DB_PASSWORD","synthetic-not-a-real-secret",
                "SHALE_RT_DB_URL","jdbc:sqlserver://127.0.0.1:9;databaseName=BootRuntime;loginTimeout=1",
                "SHALE_RT_DB_USER","synthetic-runtime","SHALE_RT_DB_PASSWORD","synthetic-not-a-real-secret",
                "SHALE_AUTH_TOKEN_SECRET","synthetic-boot-test-secret-at-least-thirty-two-characters",
                "SHALE_AUTH_SESSION_BINDING_CUTOVER_AT","2026-09-29T18:00:00Z");
        settings.forEach((key,value)->{previous.put(key,System.getProperty(key));System.setProperty(key,value);});
    }

    @AfterEach void restoreOrdinaryConfiguration() {
        previous.forEach((key,value)->{if(value==null)System.clearProperty(key);else System.setProperty(key,value);});
    }

    @Test void embeddedServletRegistrationReachesExistingChallengeAndLeavesApiUnchanged()throws Exception {
        try(var context=new SpringApplicationBuilder(ShaleServerApplication.class).profiles("dev").run(
                "--server.port=0","--server.address=127.0.0.1","--shale.ai.oauth.identity=TEST",
                "--shale.ai.oauth.public-origin=https://shale.test","--shale.ai.enabled=true","--shale.ai.mcp.enabled=true",
                "--shale.ai.cimd.enabled=false","--shale.ai.mcp.case-data-composition-enabled=false",
                "--shale.ai.mcp.search-cases-enabled=false","--shale.ai.mcp.get-case-overview-enabled=false",
                "--shale.ai.oauth.composition-enabled=false","--shale.ai.oauth.authorization-enabled=false",
                "--shale.ai.oauth.exchange-enabled=false","--shale.ai.oauth.bootstrap.jdbc-url=jdbc:sqlserver://127.0.0.1:9;databaseName=Unused",
                "--shale.ai.oauth.bootstrap.username=synthetic","--shale.ai.oauth.bootstrap.password=synthetic")) {
            int port=((WebServerApplicationContext)context).getWebServer().getPort();
            try(var client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()) {
                String origin="http://127.0.0.1:"+port;
                var denied=client.send(HttpRequest.newBuilder(URI.create(origin+"/mcp")).timeout(Duration.ofSeconds(5))
                        .header("Content-Type","application/json").header("Accept","application/json")
                        .POST(HttpRequest.BodyPublishers.ofString("{}")).build(),HttpResponse.BodyHandlers.ofString());
                assertEquals(401,denied.statusCode(),"Actual REQUEST/ASYNC dispatch must reach the existing security gate, not an unguarded SDK servlet");
                assertEquals("Bearer resource_metadata=\"https://shale.test/.well-known/oauth-protected-resource/mcp\"",denied.headers().firstValue("WWW-Authenticate").orElseThrow());
                var prm=client.send(HttpRequest.newBuilder(URI.create(origin+"/.well-known/oauth-protected-resource/mcp")).GET().build(),HttpResponse.BodyHandlers.ofString());
                assertEquals(200,prm.statusCode());assertTrue(prm.body().contains("cases.overview.read"));
                var health=client.send(HttpRequest.newBuilder(URI.create(origin+"/api/health")).GET().build(),HttpResponse.BodyHandlers.ofString());
                assertEquals(200,health.statusCode(),"Namespace registration must leave ordinary Shale authentication intact");
            }
        }
    }

    @ParameterizedTest @ValueSource(strings={"azure","prod"})
    void fullProductionBootStartsWithoutAiConfigurationAndKeepsProtocolClosed(String profile, org.springframework.boot.test.system.CapturedOutput output)throws Exception {
        var before = java.time.ZoneId.systemDefault();
        try(var context=new SpringApplicationBuilder(ShaleServerApplication.class).profiles(profile).run(
                "--server.port=0","--server.address=127.0.0.1","--shale.ai.oauth.identity=PRODUCTION",
                "--shale.ai.enabled=false","--shale.ai.mcp.enabled=false",
                "--shale.ai.mcp.case-data-composition-enabled=false",
                "--shale.ai.mcp.search-cases-enabled=false","--shale.ai.mcp.get-case-overview-enabled=false",
                "--shale.ai.oauth.composition-enabled=false","--shale.ai.oauth.authorization-enabled=false",
                "--shale.ai.oauth.exchange-enabled=false")) {
            var diagnosticLines = output.getAll().lines().filter(line -> line.contains("SHALE_JVM_STARTUP ")).toList();
            assertEquals(1, diagnosticLines.size(), "All-OFF production Boot must log exactly one JVM diagnostic");
            String replica = System.getenv("WEBSITE_INSTANCE_ID");
            if (replica == null || !replica.matches("[A-Za-z0-9_-]{1,128}")) replica = "unavailable";
            assertTrue(diagnosticLines.getFirst().endsWith("SHALE_JVM_STARTUP zoneId=" + before.getId()
                    + " javaVersion=" + Runtime.version() + " replicaId=" + replica),
                    "Startup evidence must come from the running JVM with only the three approved fields");
            assertEquals(before, java.time.ZoneId.systemDefault(), "OFF Boot diagnostic must not change time zone");
            assertNotNull(context.getBean(com.shale.server.diagnostics.JvmStartupDiagnostic.class));
            var controlLines = output.getAll().lines().filter(line -> line.contains("SHALE_AI_CONTROLS_STARTUP ")).toList();
            assertEquals(1, controlLines.size(), "All-OFF Boot must emit one separate controls INFO entry");
            StringBuilder controls = new StringBuilder("SHALE_AI_CONTROLS_STARTUP");
            for (var control : com.shale.server.diagnostics.AiStartupControls.Control.values())
                controls.append(' ').append(control.key()).append("=FALSE");
            var build = context.getBean(org.springframework.boot.info.BuildProperties.class);
            controls.append(" replicaId=").append(replica).append(" buildVersion=").append(build.getVersion())
                    .append(" buildTime=").append(build.getTime()).append(" startedAt=")
                    .append(java.time.Instant.ofEpochMilli(context.getStartupDate())).append(" startupId=");
            assertTrue(controlLines.getFirst().contains(controls),
                    "Report actual bound/consumed/conditional startup state, not merely packaged defaults");
            assertTrue(controlLines.getFirst().matches(".* startupId=[a-f0-9-]{36} readiness="
                    + ("unavailable".equals(replica) ? "BLOCKED" : "READY")),
                    "Missing replica evidence blocks release readiness without breaking ordinary Boot");
            assertEquals(List.of(profile),List.of(context.getEnvironment().getActiveProfiles()));
            assertInstanceOf(BearerTokenServerSessionResolver.class,context.getBean(ServerSessionResolver.class));
            assertInstanceOf(SqlDurableSessionStore.class,context.getBean(DurableSessionStore.class));
            assertInstanceOf(SqlRememberCredentialStore.class,context.getBean(RememberCredentialStore.class));
            assertTrue(context.getBeansOfType(McpAiCaseData.class).isEmpty());
            assertTrue(context.getBeansOfType(McpSearchHandler.class).isEmpty());
            assertTrue(context.getBeansOfType(McpOverviewHandler.class).isEmpty());
            for(String bean:List.of("mcpServlet","oauthCodeBootstrap","mcpAccessAuthenticator"))assertFalse(context.containsBean(bean),bean);
            assertEquals("disabled",context.getBean(McpCaseReadiness.class).state());
            var sources=context.getBean(DataSources.class);
            assertNull(ReflectionTestUtils.getField(sources,"aiCaseData"),"OFF startup must not allocate an AI pool.");
            int port=((WebServerApplicationContext)context).getWebServer().getPort();
            try(var client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()) {
                String origin="http://127.0.0.1:"+port;
                var health=client.send(HttpRequest.newBuilder(URI.create(origin+"/api/health")).timeout(Duration.ofSeconds(5)).GET().build(),HttpResponse.BodyHandlers.ofString());
                assertEquals(200,health.statusCode());assertEquals("{\"status\":\"ok\"}",health.body());
                for(String path:List.of("/api/auth/me","/api/v2/cases/100/overview")) {
                    var response=client.send(HttpRequest.newBuilder(URI.create(origin+path)).timeout(Duration.ofSeconds(5)).GET().build(),HttpResponse.BodyHandlers.ofString());
                    assertEquals(401,response.statusCode(),"Ordinary route must retain its authentication boundary: "+path);
                }
                for(String path:List.of("/mcp","/oauth/mcp/token","/oauth/mcp/authorize")) {
                    var response=client.send(HttpRequest.newBuilder(URI.create(origin+path)).timeout(Duration.ofSeconds(5)).POST(HttpRequest.BodyPublishers.noBody()).build(),HttpResponse.BodyHandlers.ofString());
                    assertEquals(404,response.statusCode(),"Protocol must remain disabled: "+path);
                    assertFalse(response.body().contains("access_token"));
                }
            }
            assertNull(ReflectionTestUtils.getField(sources,"aiCaseData"));
        }
    }
}
