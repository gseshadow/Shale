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
    public static void verify(DataSource ai,DataSource catalog,List<DataSource> forbidden,String database,String token,String expiryToken,String kind,Path temp)throws Exception {
        var tomcat=new org.apache.catalina.startup.Tomcat();tomcat.setBaseDir(temp.toString());tomcat.setPort(0);tomcat.getConnector().setProperty("address","127.0.0.1");
        var tc=tomcat.addContext("",temp.toString());
        var config=new com.zaxxer.hikari.HikariConfig();config.setDataSource(ai);config.setMinimumIdle(0);config.setMaximumPoolSize(5);config.setInitializationFailTimeout(-1);
        try(var pool=new com.zaxxer.hikari.HikariDataSource(config);var context=new org.springframework.web.context.support.GenericWebApplicationContext()) {
            context.setServletContext(new MockServletContext());context.getEnvironment().setActiveProfiles("test");
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("confined",Map.of(
                    "shale.ai.mcp.enabled",true,"shale.ai.enabled",true,"shale.ai.mcp.search-cases-enabled",true,"shale.ai.mcp.get-case-overview-enabled",true,
                    "shale.ai.oauth.public-origin","https://shale.test","shale.ai.oauth.identity","TEST",
                    "shale.ai.oauth.bootstrap.jdbc-url","jdbc:sqlserver://127.0.0.1:1;databaseName=unused","shale.ai.oauth.bootstrap.username","unused","shale.ai.oauth.bootstrap.password","unused")));
            var identity=new McpOAuthProperties("https://shale.test",McpOAuthProperties.Identity.TEST,false);
            // Exact restricted AI identity and live catalog checks; no attestation bypass.
            context.registerBean(McpAiCaseData.class,()->new McpAiCaseData(pool,catalog,forbidden,database,identity));
            new org.springframework.context.annotation.AnnotatedBeanDefinitionReader(context).register(com.shale.server.oauth.McpOAuthSecurityConfiguration.class,McpTransportConfiguration.class);
            context.refresh();
            var servlet=(HttpServletMcpTransport)context.getBean("mcpServlet",org.springframework.boot.web.servlet.ServletRegistrationBean.class).getServlet();
            org.apache.catalina.startup.Tomcat.addServlet(tc,"mcp",servlet).setAsyncSupported(true);tc.addServletMappingDecoded("/mcp","mcp");
            var filter=new FilterDef();filter.setFilterName("security");filter.setAsyncSupported("true");filter.setFilter(context.getBean(FilterChainProxy.class));tc.addFilterDef(filter);
            var mapping=new FilterMap();mapping.setFilterName("security");mapping.addURLPattern("/mcp");mapping.setDispatcher("REQUEST");mapping.setDispatcher("ASYNC");tc.addFilterMap(mapping);
            var body=new FilterDef();body.setFilterName("body");body.setAsyncSupported("true");body.setFilter(new McpBodyFilter());tc.addFilterDef(body);
            var bodyMapping=new FilterMap();bodyMapping.setFilterName("body");bodyMapping.addURLPattern("/mcp");bodyMapping.setDispatcher("REQUEST");bodyMapping.setDispatcher("ASYNC");tc.addFilterMapBefore(bodyMapping);
            tomcat.start();String url="http://127.0.0.1:"+tomcat.getConnector().getLocalPort()+"/mcp";
            var command=kind.equals("ts")?List.of("node","../build/mcp-transport/confined-ts.mjs"):List.of("../work/mcp-clients/bin/python","../build/mcp-transport/confined-python.py");
            var process=new ProcessBuilder(command);process.environment().put("MCP_URL",url);process.environment().put("MCP_TOKEN",token);process.environment().put("MCP_EXPIRY_TOKEN",expiryToken);
            Path output=Path.of("../work/confined-client-"+kind+".json");var child=process.redirectErrorStream(true).redirectOutput(output.toFile()).start();
            if(!child.waitFor(60,TimeUnit.SECONDS)){child.destroyForcibly();child.waitFor();fail("Confined independent client timed out");}
            String receipt=Files.readString(output).replace(token,"[REDACTED]").replace(expiryToken,"[REDACTED]");Files.writeString(output,receipt);
            assertEquals(0,child.exitValue(),"Confined independent client failed: "+receipt);
        } finally {try{tomcat.stop();}finally{tomcat.destroy();}}
    }
}
