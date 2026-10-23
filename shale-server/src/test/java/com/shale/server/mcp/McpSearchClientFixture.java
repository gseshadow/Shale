package com.shale.server.mcp;

import com.shale.core.service.CaseServicePort;
import com.shale.data.service.adapter.CaseServiceAdapter;
import com.shale.data.dao.CaseDao;
import io.modelcontextprotocol.modern.server.transport.HttpServletMcpTransport;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.apache.tomcat.util.descriptor.web.*;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.web.FilterChainProxy;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real synthetic SQL, fresh OAuth ACCESS and production port/worker/transport through each independent client. */
public final class McpSearchClientFixture {
    public static void verify(String jdbcUrl,String bootstrapUser,String bootstrapPassword,javax.sql.DataSource runtime,
            String token,String overviewToken,String revokedToken,String kind,Path temp)throws Exception {
        var tomcat=new org.apache.catalina.startup.Tomcat();tomcat.setBaseDir(temp.toString());tomcat.setPort(0);
        tomcat.getConnector().setProperty("address","127.0.0.1");var tc=tomcat.addContext("",temp.toString());
        var poolConfig=new com.zaxxer.hikari.HikariConfig();poolConfig.setDataSource(runtime);poolConfig.setMinimumIdle(0);
        poolConfig.setMaximumPoolSize(5);poolConfig.setInitializationFailTimeout(-1);
        var real=new CaseServiceAdapter(new CaseDao(()->{throw new AssertionError("No independent Case lease or hydration");}));
        var port=mock(CaseServicePort.class,org.mockito.AdditionalAnswers.delegatesTo(real));
        try(var pool=new com.zaxxer.hikari.HikariDataSource(poolConfig);var context=new org.springframework.web.context.support.GenericWebApplicationContext()) {
            context.setServletContext(new MockServletContext());context.getEnvironment().setActiveProfiles("test");
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("mcp",Map.of(
                    "shale.ai.mcp.enabled",true,"shale.ai.enabled",true,"shale.ai.mcp.search-cases-enabled",true,
                    "shale.ai.oauth.public-origin","https://shale.test","shale.ai.oauth.identity","TEST",
                    "shale.ai.oauth.bootstrap.jdbc-url",jdbcUrl,"shale.ai.oauth.bootstrap.username",bootstrapUser,
                    "shale.ai.oauth.bootstrap.password",bootstrapPassword)));
            var sources=mock(com.shale.data.config.DataSources.class);when(sources.runtime()).thenReturn(pool);
            context.registerBean(com.shale.data.config.DataSources.class,()->sources);context.registerBean(CaseServicePort.class,()->port);
            new org.springframework.context.annotation.AnnotatedBeanDefinitionReader(context).register(com.shale.server.oauth.McpOAuthSecurityConfiguration.class,McpTransportConfiguration.class);
            context.refresh();assertTrue(context.containsBean("mcpAccessAuthenticator"));assertTrue(context.containsBean("mcpSearchHandler"));
            var servlet=(HttpServletMcpTransport)context.getBean("mcpServlet",org.springframework.boot.web.servlet.ServletRegistrationBean.class).getServlet();
            org.apache.catalina.startup.Tomcat.addServlet(tc,"mcp",servlet).setAsyncSupported(true);tc.addServletMappingDecoded("/mcp","mcp");
            var definition=new FilterDef();definition.setAsyncSupported("true");definition.setFilterName("springMcp");definition.setFilter(context.getBean(FilterChainProxy.class));tc.addFilterDef(definition);
            var mapping=new FilterMap();mapping.setFilterName("springMcp");mapping.addURLPattern("/mcp");mapping.setDispatcher("REQUEST");mapping.setDispatcher("ASYNC");tc.addFilterMap(mapping);
            var body=new FilterDef();body.setFilterName("body");body.setAsyncSupported("true");body.setFilter(new McpBodyFilter());tc.addFilterDef(body);
            var bodyMapping=new FilterMap();bodyMapping.setFilterName("body");bodyMapping.addURLPattern("/mcp");bodyMapping.setDispatcher("REQUEST");bodyMapping.setDispatcher("ASYNC");tc.addFilterMapBefore(bodyMapping);
            tomcat.start();String url="http://127.0.0.1:"+tomcat.getConnector().getLocalPort()+"/mcp";
            var command=kind.equals("ts")?List.of("node","../build/mcp-transport/search-ts.mjs"):
                    List.of("../work/mcp-clients/bin/python","../build/mcp-transport/search-python.py");
            var process=new ProcessBuilder(command);process.environment().put("MCP_URL",url);process.environment().put("MCP_TOKEN",token);
            process.environment().put("MCP_OVERVIEW_TOKEN",overviewToken);process.environment().put("MCP_REVOKED_TOKEN",revokedToken);
            Path evidence=Path.of("../work/mcp-evidence");Files.createDirectories(evidence);Path log=evidence.resolve("search-client-"+kind+".json");
            process.redirectErrorStream(true).redirectOutput(log.toFile());var running=process.start();
            if(!running.waitFor(60,TimeUnit.SECONDS)){running.destroyForcibly();running.waitFor();fail("External search client timed out");}
            String output=Files.readString(log);for(String credential:List.of(token,overviewToken,revokedToken))output=output.replace(credential,"[REDACTED]");Files.writeString(log,output);
            assertEquals(0,running.exitValue(),"Independent search client failed: "+output);
            org.mockito.Mockito.verify(port,times(5)).searchMinimizedCases(any(java.sql.Connection.class),anyString(),eq(7),anyInt(),anyInt(),eq(10));
            verifyNoMoreInteractions(port);
        } finally {tomcat.stop();tomcat.destroy();}
    }
}
