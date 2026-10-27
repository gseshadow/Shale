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

    @ParameterizedTest @ValueSource(strings={"azure","prod"})
    void fullProductionBootStartsWithoutAiConfigurationAndKeepsProtocolClosed(String profile)throws Exception {
        try(var context=new SpringApplicationBuilder(ShaleServerApplication.class).profiles(profile).run(
                "--server.port=0","--server.address=127.0.0.1","--shale.ai.oauth.identity=PRODUCTION",
                "--shale.ai.enabled=false","--shale.ai.mcp.enabled=false",
                "--shale.ai.mcp.case-data-composition-enabled=false",
                "--shale.ai.mcp.search-cases-enabled=false","--shale.ai.mcp.get-case-overview-enabled=false",
                "--shale.ai.oauth.composition-enabled=false","--shale.ai.oauth.authorization-enabled=false",
                "--shale.ai.oauth.exchange-enabled=false")) {
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
