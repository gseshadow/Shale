package com.shale.server.mcp;

import com.shale.data.security.ai.VerifiedIntegrationContext;
import com.shale.server.oauth.McpOAuthProperties;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.modern.McpSchema;
import io.modelcontextprotocol.modern.server.transport.HttpServletMcpTransport;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Synthetic authority, actual SDK parser/server/servlet. Real SQL validation is a separate acceptance layer. */
class McpTransportTest {
    static final String TOKEN="a".repeat(43), ORIGIN="https://client.test";
    final McpOAuthProperties identity=new McpOAuthProperties("https://shale.test",McpOAuthProperties.Identity.TEST,false);
    final VerifiedIntegrationContext authority=mock(VerifiedIntegrationContext.class);
    final java.util.concurrent.atomic.AtomicInteger authentications=new java.util.concurrent.atomic.AtomicInteger();
    McpGate gate;
    HttpServletMcpTransport servlet;
    @BeforeEach void setup() {
        when(authority.credentialId()).thenReturn(1L);
        when(authority.effectiveScopes()).thenReturn(com.shale.core.security.ai.AiScope.fromMask(3));
        gate=new McpGate(new McpTransportProperties(true,Set.of(ORIGIN)),identity,true,token->{
            authentications.incrementAndGet();if(!TOKEN.equals(token))throw new SecurityException();return authority;
        });
        var mapper=McpTransportConfiguration.mapper();
        servlet=HttpServletMcpTransport.builder(McpTransportConfiguration.server("synthetic-build",mapper)).jsonMapper(mapper)
                .maxRequestSize(8192).httpHeaderValidator(h->{}).keepAliveInterval(null)
                .contextExtractor(r->McpTransportContext.create(Map.of(McpGate.AUTHORITY,r.getAttribute(McpGate.AUTHORITY)))).build();
    }
    @AfterEach void close() { servlet.destroy(); }
    static String body(String method,String version) {
        return "{\"jsonrpc\":\"2.0\",\"id\":\"one\",\"method\":\""+method+"\",\"params\":{\"_meta\":{\"io.modelcontextprotocol/protocolVersion\":\""+version+"\",\"io.modelcontextprotocol/clientCapabilities\":{}}}}";
    }
    MockHttpServletRequest request(String method) {
        var r=new MockHttpServletRequest("POST","/mcp");r.setServletPath("/mcp");r.setContentType("application/json");
        r.addHeader("Accept","application/json");r.addHeader("Authorization","Bearer "+TOKEN);
        r.addHeader("Mcp-Method",method);r.addHeader("MCP-Protocol-Version",McpGate.PROTOCOL);
        r.setContent(body(method,McpGate.PROTOCOL).getBytes(java.nio.charset.StandardCharsets.UTF_8));return r;
    }
    MockHttpServletResponse send(MockHttpServletRequest r)throws Exception {
        var response=new MockHttpServletResponse();
        gate.doFilter(r,response,(q,s)->servlet.service(q,s));
        assertNull(r.getAttribute(McpGate.AUTHORITY),"Transient verified context must be cleared");
        assertNull(r.getSession(false));assertNull(response.getHeader("Mcp-Session-Id"));
        assertFalse(r.isAsyncStarted());assertTrue(response.getContentAsByteArray().length<=4096);
        return response;
    }
    @Test void independentDiscoveryAndEmptyCatalogHaveFixedIdentityAndNoSession()throws Exception {
        for(String method:List.of("server/discover","tools/list","server/discover")) {
            var response=send(request(method));assertEquals(200,response.getStatus(),response.getContentAsString());
            var json=McpTransportConfiguration.mapper().getJsonMapper().readTree(response.getContentAsString());
            if(method.equals("tools/list")) assertTrue(json.path("result").path("tools").isEmpty());
            else {
                assertEquals("2026-07-28",json.path("result").path("supportedVersions").get(0).asText());
                assertTrue(response.getContentAsString().contains("shale-mcp"));
                assertTrue(response.getContentAsString().contains("synthetic-build"));
                for(String capability:List.of("resources","prompts","sampling","elicitation","tasks","roots","subscriptions","logging"))
                    assertFalse(json.path("result").path("capabilities").has(capability),capability);
            }
        }
        assertEquals(3,authentications.get(),"Every POST must independently validate ACCESS");
        assertTrue(McpCatalog.visible(authority).isEmpty());
        for(var tool:McpCatalog.FutureTool.values())assertFalse(tool.activated());
    }
    @Test void defaultOffAndIndependentGlobalOffNeverAuthenticate()throws Exception {
        for(boolean oauth:List.of(false,true)) {
            gate=new McpGate(new McpTransportProperties(false,Set.of()),new McpOAuthProperties("https://shale.test",McpOAuthProperties.Identity.TEST,oauth),true,t->{fail();return null;});
            assertEquals(404,send(request("server/discover")).getStatus());
            var r=request("server/discover");r.setRequestURI(com.shale.server.oauth.OAuthMetadataContract.PROTECTED_RESOURCE_PATH);
            assertEquals(404,send(r).getStatus());
        }
        gate=new McpGate(new McpTransportProperties(true,Set.of()),identity,false,t->{fail();return null;});
        assertEquals(401,send(request("server/discover")).getStatus());
    }
    @Test void originIsExactMissingIsAllowedAndForwardedHeadersCannotGrantTrust()throws Exception {
        for(String origin:List.of(ORIGIN,"https://CLIENT.test",ORIGIN+"/",ORIGIN+":443","null","https://attacker.test")) {
            var r=request("server/discover");r.addHeader("Origin",origin);
            r.addHeader("Host","attacker.invalid");r.addHeader("Forwarded","host=client.test;proto=https");
            r.addHeader("X-Forwarded-Host","client.test");r.addHeader("X-Forwarded-Proto","https");
            assertEquals(origin.equals(ORIGIN)?200:403,send(r).getStatus(),origin);
        }
        assertEquals(200,send(request("server/discover")).getStatus());
    }
    @Test void onlyPostIsTransportNoGetStreamAndOptionsIsBounded()throws Exception {
        for(String method:List.of("GET","PUT","PATCH","DELETE","OPTIONS","HEAD")) {
            var r=request("server/discover");r.setMethod(method);var response=send(r);
            assertEquals(405,response.getStatus());assertEquals("POST",response.getHeader("Allow"));
        }
        assertEquals(0,authentications.get());
    }
    @Test void alternateCredentialsAndDuplicateOrOversizedHeadersFailClosed()throws Exception {
        var tokens=new com.shale.server.runtime.ShaleAuthTokenService("synthetic-ordinary-shale-jwt-secret-32-bytes",3600,java.time.Clock.systemUTC());
        String jwt=tokens.issue(new com.shale.server.runtime.ServerPrincipal(42,7,null));
        assertTrue(tokens.verifyToken(jwt).isPresent());
        for(String value:List.of("Bearer "+jwt,"Bearer malformed.jwt.like","Bearer "+"b".repeat(43),"Basic bad")) {
            var r=request("server/discover");r.removeHeader("Authorization");r.addHeader("Authorization",value);
            assertTrue(send(r).getStatus()>=400);
        }
        for(String header:List.of("Authorization","Origin","MCP-Protocol-Version","Accept","Content-Type","Mcp-Method","Mcp-Name")) {
            var r=request("server/discover");r.addHeader(header,"x".repeat(1024));assertEquals(400,send(r).getStatus(),header);
        }
        for(String header:List.of("Authorization","MCP-Protocol-Version")) {
            var r=request("server/discover");r.addHeader(header,r.getHeader(header));assertEquals(400,send(r).getStatus());
        }
        var query=request("server/discover");query.setQueryString("access_token="+TOKEN);assertEquals(401,send(query).getStatus());
        var cookie=request("server/discover");cookie.setCookies(new jakarta.servlet.http.Cookie("remember",TOKEN));assertEquals(401,send(cookie).getStatus());
    }
    @Test void protocolAndHeaderBodyMismatchNeverDowngrade()throws Exception {
        for(String version:List.of("2025-11-25","2099-01-01")) {
            var r=request("server/discover");r.removeHeader("MCP-Protocol-Version");r.addHeader("MCP-Protocol-Version",version);
            r.setContent(body("server/discover",version).getBytes());var response=send(r);assertEquals(400,response.getStatus());
            assertTrue(response.getContentAsString().contains("supported"));
        }
        var mismatch=request("server/discover");mismatch.setContent(body("tools/list",McpGate.PROTOCOL).getBytes());
        assertEquals(400,send(mismatch).getStatus());
        assertEquals(404,send(request("initialize")).getStatus(),"No legacy handshake");
        var unknown=send(request("private-java-exception-name"));assertEquals(404,unknown.getStatus());
        assertFalse(unknown.getContentAsString().contains("private-java-exception-name"));
    }
    @Test void bodyAndParserAdversarialBoundariesFailDeterministically()throws Exception {
        var valid=body("server/discover",McpGate.PROTOCOL);
        for(String body:List.of("","{","[]","null","{}",valid.replace("2.0","1.0"),valid.replace("\"id\":\"one\"","\"id\":null"),
                valid.replace("\"id\":\"one\"","\"id\":1.25"),valid.replace("\"id\":\"one\"","\"id\":\""+"a".repeat(65)+"\""),
                valid.replace("\"method\":\"server/discover\"","\"method\":null"),valid.replace("\"id\":\"one\"","\"id\":\"one\",\"id\":\"two\""),
                valid.replace("\"_meta\":{","\"_meta\":{\"deep\":"+"[".repeat(10)+"0"+"]".repeat(10)+","))) {
            var r=request("server/discover");r.setContent(body.getBytes());assertEquals(400,send(r).getStatus(),body);
        }
        var big=request("server/discover");big.setContent(new byte[8193]);assertEquals(413,send(big).getStatus());
    }
    @Test void mediaAndCompressionAreExplicit()throws Exception {
        for(String type:List.of("text/plain","application/json; charset=ISO-8859-1","application/octet-stream")) {
            var r=request("server/discover");r.setContentType(type);assertEquals(415,send(r).getStatus());
        }
        for(String accept:List.of("text/event-stream","application/json;q=0","application/json;q=0,*/*;q=1","broken")) {
            var r=request("server/discover");r.removeHeader("Accept");r.addHeader("Accept",accept);assertEquals(406,send(r).getStatus());
        }
        for(String accept:List.of("*/*","application/*","application/json, text/event-stream")) {
            var r=request("server/discover");r.removeHeader("Accept");r.addHeader("Accept",accept);assertEquals(200,send(r).getStatus());
        }
        var r=request("server/discover");r.addHeader("Content-Encoding","gzip");assertEquals(400,send(r).getStatus());
    }
    @Test void protectedResourceMetadataIsCanonicalAndContainsNoScopeOrTenantInventory()throws Exception {
        var r=new MockHttpServletRequest("GET",com.shale.server.oauth.OAuthMetadataContract.PROTECTED_RESOURCE_PATH);
        r.addHeader("Host","attacker.invalid");r.addHeader("X-Forwarded-Proto","http");var response=send(r);
        assertEquals(200,response.getStatus());assertEquals(com.shale.server.oauth.OAuthMetadataContract.protectedResource(identity),
                McpTransportConfiguration.mapper().getJsonMapper().readValue(response.getContentAsString(),Map.class));
    }
    @Test void sdkPayloadLoggingIsDeniedEvenUnderDebug() {
        var logger=(ch.qos.logback.classic.Logger)org.slf4j.LoggerFactory.getLogger("io.modelcontextprotocol.modern.JsonRpc");
        var previous=logger.getLevel();try {logger.setLevel(ch.qos.logback.classic.Level.DEBUG);assertFalse(logger.isDebugEnabled());}finally {logger.setLevel(previous);}
    }
    @Test void processCeilingRejectsImmediatelyAndReleasesAfterFailure()throws Exception {
        var entered=new CountDownLatch(5);var release=new CountDownLatch(1);
        gate=new McpGate(new McpTransportProperties(true,Set.of()),identity,true,t->{entered.countDown();try {release.await(5,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}throw new SecurityException();});
        try(var executor=Executors.newFixedThreadPool(5)) {
            var futures=new ArrayList<Future<?>>();for(int i=0;i<5;i++) futures.add(executor.submit(()->{try {assertEquals(401,send(request("server/discover")).getStatus());}catch(Exception e){throw new RuntimeException(e);}}));
            assertTrue(entered.await(5,TimeUnit.SECONDS));assertEquals(429,send(request("server/discover")).getStatus());release.countDown();
            for(var future:futures)future.get(5,TimeUnit.SECONDS);
        } finally {release.countDown();}
        assertEquals(401,send(request("server/discover")).getStatus());
    }
    @Test void deadlineStartsBeforeAuthenticationAndCannotBeRestartedAtDispatch()throws Exception {
        var time=new java.util.concurrent.atomic.AtomicLong();
        gate=new McpGate(new McpTransportProperties(true,Set.of()),identity,true,t->{time.set(10_000_000_001L);return authority;},time::get);
        assertEquals(504,send(request("server/discover")).getStatus());
    }
    @Test void productionCompositionHasNoCaseReadDependencyOrAuditConsumer()throws Exception {
        for(String source:List.of("McpTransportConfiguration","McpGate","McpCatalog")) {
            var text=java.nio.file.Files.readString(java.nio.file.Path.of("src/main/java/com/shale/server/mcp/"+source+".java"));
            for(String forbidden:List.of("CaseServicePort","CaseServiceAdapter","IntegrationCaseReadService","CaseSummaryDao","IntegrationAccessAudit","ThreadLocal","getSession("))
                assertFalse(text.contains(forbidden),source+" must not reach "+forbidden);
        }
    }
}
