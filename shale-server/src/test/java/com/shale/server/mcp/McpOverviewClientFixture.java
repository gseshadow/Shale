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
public final class McpOverviewClientFixture {
    public static void verify(String jdbcUrl,String bootstrapUser,String bootstrapPassword,javax.sql.DataSource runtime,
            Map<String,String> credentials,String kind,Path temp)throws Exception {
        var tomcat=new org.apache.catalina.startup.Tomcat();tomcat.setBaseDir(temp.toString());tomcat.setPort(0);
        tomcat.getConnector().setProperty("address","127.0.0.1");var tc=tomcat.addContext("",temp.toString());
        var poolConfig=new com.zaxxer.hikari.HikariConfig();poolConfig.setDataSource(runtime);poolConfig.setMinimumIdle(0);
        poolConfig.setMaximumPoolSize(5);poolConfig.setInitializationFailTimeout(-1);
        var real=new CaseServiceAdapter(new CaseDao(()->{throw new AssertionError("No independent Case lease or hydration");}));
        var port=mock(CaseServicePort.class,org.mockito.AdditionalAnswers.delegatesTo(real));
        var reads=new java.util.concurrent.atomic.AtomicInteger();
        doAnswer(call->{reads.incrementAndGet();return real.readMinimizedCaseOverview(call.getArgument(0),call.getArgument(1),call.getArgument(2),call.getArgument(3));})
                .when(port).readMinimizedCaseOverview(any(java.sql.Connection.class),anyLong(),anyInt(),anyInt());
        var readFailure=new java.util.concurrent.atomic.AtomicReference<AssertionError>();
        var operationReceipts=Collections.synchronizedList(new ArrayList<Map<String,Object>>());
        try(var pool=new com.zaxxer.hikari.HikariDataSource(poolConfig);var context=new org.springframework.web.context.support.GenericWebApplicationContext()) {
            context.setServletContext(new MockServletContext());context.getEnvironment().setActiveProfiles("test");
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("mcp",Map.of(
                    "shale.ai.mcp.get-case-overview-enabled",true,"shale.ai.mcp.enabled",true,"shale.ai.enabled",true,"shale.ai.mcp.search-cases-enabled",true,
                    "shale.ai.oauth.public-origin","https://shale.test","shale.ai.oauth.identity","TEST",
                    "shale.ai.oauth.bootstrap.jdbc-url",jdbcUrl,"shale.ai.oauth.bootstrap.username",bootstrapUser,
                    "shale.ai.oauth.bootstrap.password",bootstrapPassword)));
            var sources=mock(com.shale.data.config.DataSources.class);when(sources.runtime()).thenReturn(pool);
            context.registerBean(com.shale.data.config.DataSources.class,()->sources);context.registerBean(CaseServicePort.class,()->port);
            new org.springframework.context.annotation.AnnotatedBeanDefinitionReader(context).register(com.shale.server.oauth.McpOAuthSecurityConfiguration.class,McpTransportConfiguration.class);
            context.refresh();assertTrue(context.containsBean("mcpAccessAuthenticator"));assertTrue(context.containsBean("mcpSearchHandler"));
            var servlet=(HttpServletMcpTransport)context.getBean("mcpServlet",org.springframework.boot.web.servlet.ServletRegistrationBean.class).getServlet();
            org.apache.catalina.startup.Tomcat.addServlet(tc,"mcp",servlet).setAsyncSupported(true);tc.addServletMappingDecoded("/mcp","mcp");
            var definition=new FilterDef();definition.setAsyncSupported("true");definition.setFilterName("springMcp");
            definition.setFilter((request,response,chain)->{
                int before=reads.get();var buffered=new org.springframework.web.util.ContentCachingResponseWrapper((jakarta.servlet.http.HttpServletResponse)response);
                context.getBean(FilterChainProxy.class).doFilter(request,buffered,chain);
                try {
                    var root=McpTransportConfiguration.responseMapper().getJsonMapper().readTree(buffered.getContentAsByteArray());
                    var result=root.path("result");String outcome="PROTOCOL";int expected=0;
                    if(result.has("isError")) {
                        if(!result.path("isError").asBoolean()){expected=1;outcome="SUCCESS";}
                        else {outcome=McpTransportConfiguration.mapper().getJsonMapper().readTree(result.path("content").get(0).path("text").asText()).path("code").asText();if(Set.of("CASE_UNAVAILABLE","PAYLOAD_TOO_LARGE","AUDIT_UNAVAILABLE").contains(outcome))expected=1;}
                    }
                    assertEquals(expected,reads.get()-before,"Exactly one authoritative minimized operation per valid Overview; none for metadata/early rejection");
                    operationReceipts.add(Map.of("outcome",outcome,"caseOperations",reads.get()-before));
                } catch(AssertionError failure) {readFailure.compareAndSet(null,failure);}
                buffered.copyBodyToResponse();
            });tc.addFilterDef(definition);
            var mapping=new FilterMap();mapping.setFilterName("springMcp");mapping.addURLPattern("/mcp");mapping.setDispatcher("REQUEST");mapping.setDispatcher("ASYNC");tc.addFilterMap(mapping);
            var body=new FilterDef();body.setFilterName("body");body.setAsyncSupported("true");body.setFilter(new McpBodyFilter());tc.addFilterDef(body);
            var bodyMapping=new FilterMap();bodyMapping.setFilterName("body");bodyMapping.addURLPattern("/mcp");bodyMapping.setDispatcher("REQUEST");bodyMapping.setDispatcher("ASYNC");tc.addFilterMapBefore(bodyMapping);
            tomcat.start();String url="http://127.0.0.1:"+tomcat.getConnector().getLocalPort()+"/mcp";
            var command=kind.equals("ts")?List.of("node","../build/mcp-transport/overview-ts.mjs"):
                    List.of("../work/mcp-clients/bin/python","../build/mcp-transport/overview-python.py");
            var process=new ProcessBuilder(command);process.environment().put("MCP_URL",url);
            credentials.forEach((key,value)->process.environment().put("MCP_"+key,value));
            Path evidence=Path.of("../work/mcp-evidence");Files.createDirectories(evidence);Path log=evidence.resolve("overview-client-"+kind+".json");
            process.redirectErrorStream(true).redirectOutput(log.toFile());var running=process.start();
            if(!running.waitFor(60,TimeUnit.SECONDS)){running.destroyForcibly();running.waitFor();fail("External search client timed out");}
            String output=Files.readString(log);for(String credential:credentials.values())output=output.replace(credential,"[REDACTED]");Files.writeString(log,output);
            assertEquals(0,running.exitValue(),"Independent search client failed: "+output);
            assertNull(readFailure.get(),"Per-request Case operation count: "+readFailure.get());
            Files.writeString(evidence.resolve("overview-operations-"+kind+".json"),McpTransportConfiguration.responseMapper().writeValueAsString(operationReceipts));
            org.mockito.Mockito.verify(port,times(7)).readMinimizedCaseOverview(any(java.sql.Connection.class),anyLong(),eq(7),anyInt());
            verifyNoMoreInteractions(port);
        } finally {tomcat.stop();tomcat.destroy();}
    }
}
