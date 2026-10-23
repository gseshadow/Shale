package com.shale.server.mcp;

import com.shale.data.security.ai.*;
import io.modelcontextprotocol.modern.server.transport.HttpServletMcpTransport;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.apache.tomcat.util.descriptor.web.*;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.*;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real loopback Tomcat + Spring security + SDK transport, using the supplied real SQL ACCESS validator. */
public final class McpClientInteropFixture {
    public static void verifyClients(javax.sql.DataSource bootstrap,javax.sql.DataSource runtime,String issuer,String resource,String TOKEN,Path temp)throws Exception {
        McpTransportConfiguration.DeadlineAccess access=(token,deadline)-> {
            var bootstrapLease=new McpJdbcDeadline(bootstrap,deadline);
            return new AiAccessTokenValidator(()-> {try{return bootstrapLease.getConnection();}catch(java.sql.SQLException failure){throw new SecurityException();}},
                    new McpJdbcDeadline(runtime,deadline),true,issuer,resource).validate(token);
        };
        var tomcat=new org.apache.catalina.startup.Tomcat();tomcat.setBaseDir(temp.toString());tomcat.setPort(0);
        tomcat.getConnector().setProperty("address","127.0.0.1");var tc=tomcat.addContext("",temp.toString());
        try(var context=new org.springframework.web.context.support.GenericWebApplicationContext()) {
            context.setServletContext(new MockServletContext());context.getEnvironment().setActiveProfiles("test");
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("mcp",Map.of(
                    "shale.ai.mcp.enabled",true,"shale.ai.enabled",true,"shale.ai.mcp.allowed-origins[0]","https://client.test",
                    "shale.ai.oauth.public-origin","https://shale.test","shale.ai.oauth.identity","TEST")));
            context.registerBean(McpTransportConfiguration.DeadlineAccess.class,()->access);
            new org.springframework.context.annotation.AnnotatedBeanDefinitionReader(context).register(com.shale.server.oauth.McpOAuthSecurityConfiguration.class,McpTransportConfiguration.class);
            context.refresh();
            assertTrue(context.getBeansOfType(com.shale.core.service.CaseServicePort.class).isEmpty());
            var registration=context.getBean("mcpServlet",org.springframework.boot.web.servlet.ServletRegistrationBean.class);
            var servlet=(HttpServletMcpTransport)registration.getServlet();
            org.apache.catalina.startup.Tomcat.addServlet(tc,"mcp",servlet).setAsyncSupported(true);tc.addServletMappingDecoded("/mcp","mcp");
            var definition=new FilterDef();definition.setAsyncSupported("true");definition.setFilterName("springMcp");definition.setFilter(context.getBean(FilterChainProxy.class));tc.addFilterDef(definition);
            var mapping=new FilterMap();mapping.setFilterName("springMcp");mapping.addURLPattern("/mcp");mapping.setDispatcher("REQUEST");mapping.setDispatcher("ASYNC");tc.addFilterMap(mapping);
            var body=new FilterDef();body.setFilterName("body");body.setAsyncSupported("true");body.setFilter(new McpBodyFilter());tc.addFilterDef(body);
            var bodyMapping=new FilterMap();bodyMapping.setFilterName("body");bodyMapping.addURLPattern("/mcp");bodyMapping.setDispatcher("REQUEST");bodyMapping.setDispatcher("ASYNC");tc.addFilterMapBefore(bodyMapping);
            var off=new FilterDef();off.setFilterName("off");off.setFilter(new McpGate(new McpTransportProperties(false,Set.of()),
                    new com.shale.server.oauth.McpOAuthProperties(null,null,false),false,t->{throw new AssertionError();}));tc.addFilterDef(off);
            var offMapping=new FilterMap();offMapping.setFilterName("off");offMapping.addURLPattern("/off");tc.addFilterMap(offMapping);
            tomcat.start();String base="http://127.0.0.1:"+tomcat.getConnector().getLocalPort();
            try(var socket=new java.net.Socket("127.0.0.1",tomcat.getConnector().getLocalPort())) {
                socket.setSoTimeout(13000);
                socket.getOutputStream().write(("POST /mcp HTTP/1.1\r\nHost: localhost\r\nContent-Type: application/json\r\nContent-Length: 64\r\n\r\n{").getBytes(java.nio.charset.StandardCharsets.US_ASCII));
                var reader=new java.io.BufferedReader(new java.io.InputStreamReader(socket.getInputStream()));
                assertTrue(reader.readLine().contains("504"),"Slow body must time out before authentication, with no detached work");
            }
            Path evidence=Path.of("../work/mcp-evidence");Files.createDirectories(evidence);
            for(var command:List.of(List.of("node","../build/mcp-transport/client-ts.mjs"),
                    List.of("../work/mcp-clients/bin/python","../build/mcp-transport/client-python.py"))) {
                var process=new ProcessBuilder(command);process.environment().put("MCP_URL",base+"/mcp");process.environment().put("MCP_OFF_URL",base+"/off");
                process.environment().put("MCP_TOKEN",TOKEN);
                Path log=evidence.resolve(command.getFirst().equals("node")?"client-ts.json":"client-python.json");
                process.redirectErrorStream(true).redirectOutput(log.toFile());var running=process.start();
                if(!running.waitFor(60,TimeUnit.SECONDS)){running.destroyForcibly();running.waitFor();fail("External MCP client timed out");}
                String output=Files.readString(log).replace(TOKEN,"[REDACTED]");Files.writeString(log,output);
                assertEquals(0,running.exitValue(),"External client failed: "+output);
            }
            Files.writeString(evidence.resolve("conformance-not-applicable.txt"),"dns-rebinding-protection: upstream explicitly targets unauthenticated localhost servers; Shale requires ACCESS authentication and exact configured Origin. Own authenticated Origin/proxy matrix executed instead.\n");
            for(String scenario:List.of("server-stateless","tools-list")) {
                var process=new ProcessBuilder("node","--import","../build/mcp-transport/conformance-auth.mjs",
                        "../build/mcp-transport/node_modules/@modelcontextprotocol/conformance/dist/index.js","server",
                        "--url",base+"/mcp","--scenario",scenario,"--spec-version","2026-07-28","--timeout","5000",
                        "--output-dir","../work/mcp-evidence/conformance");
                process.environment().put("MCP_URL",base+"/mcp");process.environment().put("MCP_TOKEN",TOKEN);
                Path log=evidence.resolve("conformance-"+scenario+".log");process.redirectErrorStream(true).redirectOutput(log.toFile());
                var running=process.start();
                if(!running.waitFor(60,TimeUnit.SECONDS)){running.destroyForcibly();running.waitFor();}
                // Record individual outcomes, including unsupported fixture requirements; no full-conformance claim.
                Files.writeString(evidence.resolve("conformance-"+scenario+".exit"),Integer.toString(running.exitValue()));
                System.out.println("MCP conformance "+scenario+" exit="+running.exitValue());
                try(var files=Files.walk(evidence)) {
                    for(Path file:files.filter(Files::isRegularFile).toList()) {
                        String value=Files.readString(file);if(value.contains(TOKEN))Files.writeString(file,value.replace(TOKEN,"[REDACTED]"));
                    }
                }
            }
            int passed=0,skipped=0,unavailable=0;
            var notApplicable=Set.of("sep-2575-server-rejects-undeclared-capability","sep-2575-missing-capability-http-400",
                    "sep-2575-http-server-no-independent-requests-on-stream","sep-2575-server-no-log-without-loglevel");
            var reportMapper=new tools.jackson.databind.ObjectMapper();
            try(var reports=Files.walk(evidence.resolve("conformance"))) {
                for(Path report:reports.filter(p->p.getFileName().toString().equals("checks.json")).toList()) {
                    for(var check:reportMapper.readTree(Files.readString(report))) {
                        String status=check.path("status").asText();
                        if(status.equals("SUCCESS"))passed++;
                        if(status.equals("SKIPPED"))skipped++;
                        if(status.equals("FAILURE")) {
                            assertTrue(notApplicable.contains(check.path("id").asText()) && check.path("errorMessage").asText().startsWith("Not testable: server does not list the diagnostic tool"),
                                    "Applicable upstream conformance failure: "+check.path("id").asText());unavailable++;
                        }
                    }
                }
            }
            assertTrue(passed>0,"Upstream conformance checks must actually execute");
            Files.writeString(evidence.resolve("conformance-applicability.json"),reportMapper.writeValueAsString(Map.of(
                    "applicablePassed",passed,"upstreamSkipped",skipped,"missingDiagnosticFixtures",unavailable,
                    "fullConformanceClaimed",false)));
        } finally {tomcat.stop();tomcat.destroy();}
    }
}
