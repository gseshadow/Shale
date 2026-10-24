package com.shale.server.mcp;

import com.shale.core.security.ai.AiScope;
import com.shale.data.security.ai.*;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.modern.McpSchema;
import io.modelcontextprotocol.modern.server.transport.HttpServletMcpTransport;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Actual SDK wire behavior; mocked completed disclosure is separate from the real synthetic SQL fixture. */
class McpSearchTest {
    final VerifiedIntegrationContext authority=mock(VerifiedIntegrationContext.class);
    final IntegrationCaseReadService service=mock(IntegrationCaseReadService.class);
    final McpSearchHandler handler=new McpSearchHandler(deadline->service);
    HttpServletMcpTransport servlet;
    @BeforeEach void setup() {
        when(authority.credentialId()).thenReturn(1L);when(authority.effectiveScopes()).thenReturn(AiScope.fromMask(3));
        when(authority.deadline()).thenReturn(java.time.Instant.now().plusSeconds(60));
        var mapper=McpTransportConfiguration.mapper();
        servlet=HttpServletMcpTransport.builder(McpTransportConfiguration.server("test",mapper,handler)).jsonMapper(mapper)
                .httpHeaderValidator(h->{}).keepAliveInterval(null)
                .contextExtractor(r->McpTransportContext.create(Map.of(McpGate.AUTHORITY,authority,McpGate.DEADLINE,System.nanoTime()+9_000_000_000L))).build();
    }
    @AfterEach void close() { servlet.destroy(); }
    tools.jackson.databind.JsonNode send(String method,String name,Map<String,Object> args)throws Exception {
        var r=new MockHttpServletRequest("POST","/mcp");r.setContentType("application/json");r.addHeader("Accept","application/json");
        r.addHeader("Mcp-Method",method);r.addHeader("MCP-Protocol-Version",McpGate.PROTOCOL);if(name!=null)r.addHeader("Mcp-Name",name);
        var params=new LinkedHashMap<String,Object>();params.put("_meta",Map.of(McpSchema.MetaKeys.PROTOCOL_VERSION,McpGate.PROTOCOL,McpSchema.MetaKeys.CLIENT_CAPABILITIES,Map.of()));
        if(name!=null){params.put("name",name);params.put("arguments",args);}
        var mapper=McpTransportConfiguration.mapper();r.setContent(mapper.writeValueAsBytes(new io.modelcontextprotocol.modern.JsonRpc.JSONRPCRequest(method,"wire",params)));
        var response=new MockHttpServletResponse();servlet.service(r,response);
        return McpTransportConfiguration.responseMapper().getJsonMapper().readTree(response.getContentAsString());
    }
    @Test void advertisedWireSchemaExactlyMatchesCanonicalResourceAndScopeIsIndependent()throws Exception {
        var mapper=McpTransportConfiguration.responseMapper();
        var tools=send("tools/list",null,null).path("result").path("tools");assertEquals(1,tools.size());
        try(var in=getClass().getResourceAsStream("/mcp/search-cases.json")) {assertEquals(mapper.getJsonMapper().readTree(in),tools.get(0));}
        for(int mask:List.of(0,2)) {when(authority.effectiveScopes()).thenReturn(AiScope.fromMask(mask));assertTrue(send("tools/list",null,null).path("result").path("tools").isEmpty());}
        when(authority.effectiveScopes()).thenReturn(AiScope.fromMask(1));assertEquals(1,send("tools/list",null,null).path("result").path("tools").size());
        for(String name:List.of("get_case_overview","arbitrary"))assertEquals(-32602,send("tools/call",name,Map.of()).path("error").path("code").asInt());
        verifyNoInteractions(service);
    }
    @Test void wireValidationNeverTurnsMissingNullOrMalformedArgumentsIntoBrowse()throws Exception {
        var invalid=new ArrayList<Map<String,Object>>(List.of(Map.of(),Map.of("query","ab"),Map.of("query","abc","page",-1),
                Map.of("query","abc","page",10),Map.of("query","abc","page",0.5),Map.of("query","abc","size",10),Map.of("query","abc","tenantId",7),Map.of("query","x".repeat(101))));
        var nil=new HashMap<String,Object>();nil.put("query",null);invalid.add(nil);
        nil=new HashMap<>();nil.put("query","abc");nil.put("page",null);invalid.add(nil);
        for(var args:invalid)send("tools/call","search_cases",args);
        verify(service,times(invalid.size())).search(any(IntegrationInvocation.class),isNull(),isNull(),any());
    }
    @Test void oneCallConsumesOnlyCompletedBufferPreservingNullsPageAndIdenticalText()throws Exception {
        String json="{\"items\":[],\"page\":0,\"size\":10,\"hasMore\":false,\"windowExhausted\":false,\"correlationId\":\""+UUID.randomUUID()+"\"}";
        var buffer=mock(DisclosureBuffer.class);when(buffer.bytes()).thenAnswer(i->ByteBuffer.wrap(json.getBytes(StandardCharsets.UTF_8)).asReadOnlyBuffer());
        when(service.search(any(IntegrationInvocation.class),eq("  literal  "),eq(0),any())).thenAnswer(i->{
            var measure=i.getArgument(3,java.util.function.ToIntFunction.class);assertTrue(measure.applyAsInt(buffer)<65536);return buffer;
        });
        var result=send("tools/call","search_cases",Map.of("query","  literal  ")).path("result");
        assertFalse(result.path("isError").asBoolean(),result.toString());assertEquals("complete",result.path("resultType").asText());publicMetadataOnly(result);
        assertEquals(result.path("structuredContent"),McpTransportConfiguration.mapper().getJsonMapper().readTree(result.path("content").get(0).path("text").asText()));
        verify(service).search(any(IntegrationInvocation.class),eq("  literal  "),eq(0),any());verifyNoMoreInteractions(service);
    }
    @Test void productionAndAzureCannotActivateTheHandler() {
        var config=new McpSearchConfiguration();
        for(String profile:List.of("prod","azure","local")) {
            var env=new org.springframework.mock.env.MockEnvironment();env.setActiveProfiles(profile);
            assertThrows(IllegalStateException.class,()->config.mcpSearchHandler(env,new com.shale.server.oauth.McpOAuthProperties("https://shale.test",com.shale.server.oauth.McpOAuthProperties.Identity.TEST,false),null,null));
        }
    }
    @Test void duplicateTextEscapingCanExceedFullEnvelopeWhileStructuredContentFits()throws Exception {
        var items=new ArrayList<Map<String,Object>>();
        for(int n=1;n<=10;n++) {
            var item=new LinkedHashMap<String,Object>();item.put("caseId",n);item.put("caseNumber","\u0001".repeat(200));item.put("caseName","literal"+"\u0001".repeat(75));
            var status=new LinkedHashMap<String,Object>();status.put("id",1);status.put("name","\u0001".repeat(200));status.put("color",null);item.put("status",status);
            for(String key:List.of("practiceArea","responsibleAttorney","primaryLegalAssistant","updatedAt"))item.put(key,null);items.add(item);
        }
        var value=Map.<String,Object>of("items",items,"page",0,"size",10,"hasMore",false,"windowExhausted",false,"correlationId",UUID.randomUUID().toString());
        assertTrue(io.modelcontextprotocol.json.McpJsonDefaults.getSchemaValidator().validate(McpSearchHandler.TOOL.outputSchema(),value).valid());
        byte[] json=McpTransportConfiguration.responseMapper().writeValueAsBytes(value);assertTrue(json.length<32768);
        var buffer=mock(DisclosureBuffer.class);when(buffer.bytes()).thenAnswer(i->ByteBuffer.wrap(json).asReadOnlyBuffer());
        var fault=mock(IntegrationReadException.class);when(fault.outcome()).thenReturn(IntegrationAccessAudit.Outcome.UNAVAILABLE);when(fault.reason()).thenReturn(IntegrationAccessAudit.Reason.OVERSIZED);
        when(service.search(any(IntegrationInvocation.class),eq("literal"),eq(0),any())).thenAnswer(i->{
            assertTrue(i.getArgument(3,java.util.function.ToIntFunction.class).applyAsInt(buffer)>65536,"Actual duplicate/escaping/SDK identity bytes must be counted");throw fault;
        });
        var result=send("tools/call","search_cases",Map.of("query","literal")).path("result");
        assertTrue(result.path("isError").asBoolean());assertFalse(result.has("structuredContent"));
        assertEquals("PAYLOAD_TOO_LARGE",McpTransportConfiguration.mapper().getJsonMapper().readTree(result.path("content").get(0).path("text").asText()).path("code").asText());
        verify(service).search(any(IntegrationInvocation.class),eq("literal"),eq(0),any());
    }
    @Test void applicationFailuresBecomeClosedToolErrorsWithoutPrivateMetadata()throws Exception {
        for(String code:List.of("INVALID_INPUT","FORBIDDEN","THROTTLED","TIMEOUT","AUDIT_UNAVAILABLE","PAYLOAD_TOO_LARGE","UNAVAILABLE")) {
            var fault=mock(IntegrationReadException.class);
            when(fault.outcome()).thenReturn(switch(code){
                case "INVALID_INPUT" -> IntegrationAccessAudit.Outcome.INVALID_INPUT;
                case "FORBIDDEN" -> IntegrationAccessAudit.Outcome.DENIED;
                case "THROTTLED" -> IntegrationAccessAudit.Outcome.THROTTLED;
                default -> IntegrationAccessAudit.Outcome.UNAVAILABLE;
            });
            when(fault.reason()).thenReturn(switch(code){
                case "TIMEOUT" -> IntegrationAccessAudit.Reason.DEADLINE;
                case "AUDIT_UNAVAILABLE" -> IntegrationAccessAudit.Reason.AUDIT;
                case "PAYLOAD_TOO_LARGE" -> IntegrationAccessAudit.Reason.OVERSIZED;
                default -> IntegrationAccessAudit.Reason.READ;
            });
            when(service.search(any(IntegrationInvocation.class),anyString(),anyInt(),any())).thenThrow(fault);
            var result=send("tools/call","search_cases",Map.of("query","literal")).path("result");
            assertTrue(result.path("isError").asBoolean(),result.toString());assertFalse(result.has("structuredContent"));publicMetadataOnly(result);
            var value=McpTransportConfiguration.mapper().getJsonMapper().readTree(result.path("content").get(0).path("text").asText());
            assertEquals(code,value.path("code").asText());UUID.fromString(value.path("correlationId").asText());
            if(code.equals("THROTTLED"))assertEquals(1,value.path("retryAfterSeconds").asInt());else assertTrue(value.path("retryAfterSeconds").isNull());
        }
    }
    private static void publicMetadataOnly(tools.jackson.databind.JsonNode result) {
        var meta=result.path("_meta");assertEquals(1,meta.size());
        assertEquals("shale-mcp",meta.path(McpSchema.MetaKeys.SERVER_INFO).path("name").asText());
        assertEquals("test",meta.path(McpSchema.MetaKeys.SERVER_INFO).path("version").asText());
    }
}
