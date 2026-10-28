package com.shale.server.mcp;

import com.shale.server.oauth.McpOAuthProperties;
import io.modelcontextprotocol.modern.server.transport.HttpServletMcpTransport;
import javax.sql.DataSource;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.apache.tomcat.util.descriptor.web.*;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.web.FilterChainProxy;
import static org.junit.jupiter.api.Assertions.*;

/** Scoped two-client regression over production proof/SQL/transport. Explicit loopback fixture TLS only. */
public final class McpConfinedClientFixture {
    @FunctionalInterface public interface ServingCheck { void run(String url)throws Exception; }
    public static void verify(DataSource ai,DataSource catalog,List<DataSource> forbidden,String database,String token,String expiryToken,String kind,Path temp)throws Exception {
        withServer(ai,catalog,forbidden,database,temp,url->{
            var command=kind.equals("ts")?List.of("node","../build/mcp-transport/confined-ts.mjs"):List.of("../work/mcp-clients/bin/python","../build/mcp-transport/confined-python.py");
            var process=new ProcessBuilder(command);process.environment().put("MCP_URL",url);process.environment().put("MCP_TOKEN",token);process.environment().put("MCP_EXPIRY_TOKEN",expiryToken);
            Path output=Path.of("../work/confined-client-"+kind+".json");var child=process.redirectErrorStream(true).redirectOutput(output.toFile()).start();
            if(!child.waitFor(60,TimeUnit.SECONDS)){child.destroyForcibly();child.waitFor();fail("Confined independent client timed out");}
            String receipt=Files.readString(output).replace(token,"[REDACTED]").replace(expiryToken,"[REDACTED]");Files.writeString(output,receipt);
            assertEquals(0,child.exitValue(),"Confined independent client failed: "+receipt);
        });
    }
    public static void withServer(DataSource ai,DataSource catalog,List<DataSource> forbidden,String database,Path temp,ServingCheck check)throws Exception {
        withServer(ai,catalog,forbidden,database,temp,java.util.function.Function.identity(),check);
    }
    public static void withServer(DataSource ai,DataSource catalog,List<DataSource> forbidden,String database,Path temp,java.util.function.Function<DataSource,DataSource> decorate,ServingCheck check)throws Exception {
        boolean production=Boolean.getBoolean("shale.acceptance.production");
        String origin=production?"https://shale.example":"https://shale.test";
        var tomcat=new org.apache.catalina.startup.Tomcat();tomcat.setBaseDir(temp.toString());tomcat.setPort(0);tomcat.getConnector().setProperty("address","127.0.0.1");
        var tc=tomcat.addContext("",temp.toString());
        var config=new com.zaxxer.hikari.HikariConfig();config.setDataSource(ai);config.setMinimumIdle(0);config.setMaximumPoolSize(5);config.setConnectionTimeout(2000);config.setInitializationFailTimeout(-1);
        try(var pool=new com.zaxxer.hikari.HikariDataSource(config);var context=new org.springframework.web.context.support.GenericWebApplicationContext()) {
            context.setServletContext(new MockServletContext());context.getEnvironment().setActiveProfiles(production?"azure":"test");
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("confined",Map.of(
                    "shale.ai.mcp.enabled",true,"shale.ai.enabled",true,"shale.ai.mcp.search-cases-enabled",true,"shale.ai.mcp.get-case-overview-enabled",true,
                    "shale.ai.oauth.public-origin",origin,"shale.ai.oauth.identity",production?"PRODUCTION":"TEST",
                    "shale.ai.oauth.bootstrap.jdbc-url","jdbc:sqlserver://127.0.0.1:1;databaseName=unused","shale.ai.oauth.bootstrap.username","unused","shale.ai.oauth.bootstrap.password","unused","shale.ai.mcp.allowed-origins",Set.of(origin))));
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("production-gate",Map.of("shale.ai.mcp.case-data-composition-enabled",production,"shale.ai.oauth.composition-enabled",production)));
            var identity=new McpOAuthProperties(origin,production?McpOAuthProperties.Identity.PRODUCTION:McpOAuthProperties.Identity.TEST,false);
            // Exact restricted AI identity and live catalog checks; no attestation bypass.
            DataSource decorated=decorate.apply(pool);
            var available=new java.util.concurrent.atomic.AtomicBoolean(true);
            DataSource serving=(DataSource)java.lang.reflect.Proxy.newProxyInstance(DataSource.class.getClassLoader(),new Class<?>[]{DataSource.class},(proxy,method,args)->{
                if(method.getName().equals("getConnection")&&!available.get())throw new java.sql.SQLException("Synthetic confined boundary unavailable");
                try{return method.invoke(decorated,args);}catch(java.lang.reflect.InvocationTargetException failure){throw failure.getCause();}
            });
            var fallbackCalls=new java.util.concurrent.atomic.AtomicInteger();
            if(production)context.registerBean("forbiddenLegacyFallback",McpTransportConfiguration.DeadlineAccess.class,()->(token,deadline)->{fallbackCalls.incrementAndGet();throw new AssertionError("Production must never invoke legacy authority");});
            context.registerBean(McpAiCaseData.class,()->new McpAiCaseData(serving,catalog,production?List.of(forbidden.get(0),forbidden.get(0),forbidden.get(1)):forbidden,database,identity,com.shale.data.security.ai.AiCaseDataAttestation.Contract.valueOf(System.getProperty("shale.acceptance.confinement","V1"))));
            new org.springframework.context.annotation.AnnotatedBeanDefinitionReader(context).register(com.shale.server.oauth.McpOAuthSecurityConfiguration.class,McpTransportConfiguration.class);
            context.refresh();
            assertEquals(List.of(production?"azure":"test"),List.of(context.getEnvironment().getActiveProfiles()));
            if(production)assertFalse(context.containsBean("mcpAccessAuthenticator"));
            assertEquals("ready",context.getBean(McpCaseReadiness.class).state());
            assertNotNull(context.getBean(McpSearchHandler.class));
            assertNotNull(context.getBean(McpOverviewHandler.class));
            var servlet=(HttpServletMcpTransport)context.getBean("mcpServlet",org.springframework.boot.web.servlet.ServletRegistrationBean.class).getServlet();
            org.apache.catalina.startup.Tomcat.addServlet(tc,"mcp",servlet).setAsyncSupported(true);tc.addServletMappingDecoded("/mcp","mcp");
            org.apache.catalina.startup.Tomcat.addServlet(tc,"closedMetadataTerminal",new jakarta.servlet.http.HttpServlet(){
                @Override protected void service(jakarta.servlet.http.HttpServletRequest request,jakarta.servlet.http.HttpServletResponse response)throws java.io.IOException {response.sendError(404);}
            });
            tc.addServletMappingDecoded(com.shale.server.oauth.OAuthMetadataContract.PROTECTED_RESOURCE_PATH,"closedMetadataTerminal");
            tc.addServletMappingDecoded(com.shale.server.oauth.OAuthMetadataContract.AUTHORIZATION_SERVER_PATH,"closedMetadataTerminal");
            var filter=new FilterDef();filter.setFilterName("security");filter.setAsyncSupported("true");filter.setFilter(context.getBean(FilterChainProxy.class));tc.addFilterDef(filter);
            var mapping=new FilterMap();mapping.setFilterName("security");mapping.addURLPattern("/mcp");mapping.addURLPattern(com.shale.server.oauth.OAuthMetadataContract.PROTECTED_RESOURCE_PATH);mapping.addURLPattern(com.shale.server.oauth.OAuthMetadataContract.AUTHORIZATION_SERVER_PATH);mapping.setDispatcher("REQUEST");mapping.setDispatcher("ASYNC");tc.addFilterMap(mapping);
            var body=new FilterDef();body.setFilterName("body");body.setAsyncSupported("true");body.setFilter(new McpBodyFilter());tc.addFilterDef(body);
            var bodyMapping=new FilterMap();bodyMapping.setFilterName("body");bodyMapping.addURLPattern("/mcp");bodyMapping.setDispatcher("REQUEST");bodyMapping.setDispatcher("ASYNC");tc.addFilterMapBefore(bodyMapping);
            tomcat.start();String url="http://127.0.0.1:"+tomcat.getConnector().getLocalPort()+"/mcp";
            check.run(url);
            if(production) {
                available.set(false);
                assertEquals("not-ready",context.getBean(McpCaseReadiness.class).state());
                try(var client=java.net.http.HttpClient.newHttpClient()) {
                    var request=java.net.http.HttpRequest.newBuilder(java.net.URI.create(url)).header("Authorization","Bearer "+"a".repeat(43)).header("Content-Type","application/json").header("Accept","application/json").POST(java.net.http.HttpRequest.BodyPublishers.ofString("{}")).build();
                    var response=client.send(request,java.net.http.HttpResponse.BodyHandlers.ofString());
                    assertEquals(401,response.statusCode());assertFalse(response.body().contains("Direct overview"));assertFalse(response.body().contains("MCP literal"));
                }
                assertEquals(0,fallbackCalls.get(),"Unavailable confinement must not consult ordinary ACCESS authority");
            }
        } finally {try{tomcat.stop();}finally{tomcat.destroy();}}
    }
}
