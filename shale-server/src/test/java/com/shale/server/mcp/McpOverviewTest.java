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
class McpOverviewTest {
    final VerifiedIntegrationContext authority=mock(VerifiedIntegrationContext.class);
    final IntegrationCaseReadService service=mock(IntegrationCaseReadService.class);
    final McpSearchHandler handlerSearch=new McpSearchHandler(deadline->service);
    final McpOverviewHandler handler=new McpOverviewHandler(deadline->service);
    HttpServletMcpTransport servlet;
    @BeforeEach void setup() {
        when(authority.credentialId()).thenReturn(1L);when(authority.effectiveScopes()).thenReturn(AiScope.fromMask(3));
        when(authority.deadline()).thenReturn(java.time.Instant.now().plusSeconds(60));
        var mapper=McpTransportConfiguration.mapper();
        servlet=HttpServletMcpTransport.builder(McpTransportConfiguration.server("test",mapper,handlerSearch,handler)).jsonMapper(mapper)
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
    @Test void exactComposedSchemaAndIndependentScopeMatrixWithoutCatalogCache()throws Exception {
        var mapper=McpTransportConfiguration.responseMapper();
        var overview=mapper.getJsonMapper().readTree(getClass().getResourceAsStream("/mcp/get-case-overview.json"));
        ((tools.jackson.databind.node.ObjectNode)overview.path("outputSchema")).set("$defs",
                mapper.getJsonMapper().readTree(getClass().getResourceAsStream("/mcp/search-cases.json")).path("outputSchema").path("$defs"));
        for(int mask:List.of(3,1,2,0,3,0)) {
            when(authority.effectiveScopes()).thenReturn(AiScope.fromMask(mask));
            var tools=send("tools/list",null,null).path("result").path("tools");
            assertEquals(Integer.bitCount(mask),tools.size());
            if((mask&2)!=0)assertEquals(overview,tools.get((mask&1)!=0?1:0));
        }
        verifyNoInteractions(service);
        when(authority.effectiveScopes()).thenReturn(AiScope.fromMask(1));
        assertEquals(-32602,send("tools/call","get_case_overview",Map.of("caseId",100)).path("error").path("code").asInt());
        when(authority.effectiveScopes()).thenReturn(AiScope.fromMask(2));
        assertEquals(-32602,send("tools/call","search_cases",Map.of("query","literal")).path("error").path("code").asInt());
        when(authority.credentialId()).thenReturn(null);
        assertThrows(SecurityException.class,()->new McpCatalog(handlerSearch,handler,McpSchema.Implementation.builder("shale-mcp","test").build()).tools(authority));
    }
    @Test void strictIdsRejectNullMissingStringsFloatsAndAdditionalAuthority()throws Exception {
        var invalid=new ArrayList<Map<String,Object>>(List.of(Map.of(),Map.of("caseId",0),Map.of("caseId",-1),Map.of("caseId",2147483648L),
                Map.of("caseId","100"),Map.of("caseId",1.5),Map.of("caseId",1.0),Map.of("caseId",100,"tenantId",7),Map.of("caseId",100,"userId",72)));
        var nil=new HashMap<String,Object>();nil.put("caseId",null);invalid.add(nil);
        for(var args:invalid)send("tools/call","get_case_overview",args);
        verify(service,times(invalid.size())).overview(any(IntegrationInvocation.class),isNull(),any());
        verifyNoMoreInteractions(service);
    }
    @Test void directOverviewUsesOneCompletedBufferWithoutSearchAndPreservesRequiredNulls()throws Exception {
        String json="{\"case\":{\"caseId\":100,\"caseNumber\":null,\"caseName\":\"name\",\"status\":null,\"practiceArea\":null,\"responsibleAttorney\":null,\"primaryLegalAssistant\":null,\"updatedAt\":null},\"correlationId\":\""+UUID.randomUUID()+"\"}";
        var buffer=mock(DisclosureBuffer.class);when(buffer.bytes()).thenAnswer(i->ByteBuffer.wrap(json.getBytes(StandardCharsets.UTF_8)).asReadOnlyBuffer());
        when(service.overview(any(IntegrationInvocation.class),eq(100L),any())).thenAnswer(i->{
            assertEquals(IntegrationInvocation.Operation.OVERVIEW,i.getArgument(0,IntegrationInvocation.class).operation());
            assertTrue(i.getArgument(2,java.util.function.ToIntFunction.class).applyAsInt(buffer)<32768);return buffer;
        });
        when(authority.effectiveScopes()).thenReturn(AiScope.fromMask(2));
        var result=send("tools/call","get_case_overview",Map.of("caseId",100)).path("result");
        assertFalse(result.path("isError").asBoolean(),result.toString());publicMetadataOnly(result);
        assertEquals(8,result.path("structuredContent").path("case").size());
        assertEquals(result.path("structuredContent"),McpTransportConfiguration.mapper().getJsonMapper().readTree(result.path("content").get(0).path("text").asText()));
        verify(service).overview(any(IntegrationInvocation.class),eq(100L),any());verifyNoMoreInteractions(service);
    }
    @Test void productionAndAzureCannotActivateOverviewEvenWithTestProfile() {
        for(String profile:List.of("prod","azure","local")) {
            var env=new org.springframework.mock.env.MockEnvironment();env.setActiveProfiles("test",profile);
            if(profile.equals("local"))env.setActiveProfiles("local");
            assertThrows(IllegalStateException.class,()->new McpOverviewConfiguration().mcpOverviewHandler(env,
                    new com.shale.server.oauth.McpOAuthProperties("https://shale.test",com.shale.server.oauth.McpOAuthProperties.Identity.TEST,false),null,null));
        }
    }
    @Test void errorsAreClosedAndUnavailableCategoriesHaveOneShape()throws Exception {
        for(String code:List.of("INVALID_INPUT","FORBIDDEN","CASE_UNAVAILABLE","THROTTLED","TIMEOUT","AUDIT_UNAVAILABLE","PAYLOAD_TOO_LARGE","UNAVAILABLE")) {
            var fault=mock(IntegrationReadException.class);
            when(fault.outcome()).thenReturn(switch(code){case "INVALID_INPUT"->IntegrationAccessAudit.Outcome.INVALID_INPUT;case "FORBIDDEN"->IntegrationAccessAudit.Outcome.DENIED;case "THROTTLED"->IntegrationAccessAudit.Outcome.THROTTLED;default->IntegrationAccessAudit.Outcome.UNAVAILABLE;});
            when(fault.reason()).thenReturn(switch(code){case "CASE_UNAVAILABLE"->IntegrationAccessAudit.Reason.CASE_UNAVAILABLE;case "TIMEOUT"->IntegrationAccessAudit.Reason.DEADLINE;case "AUDIT_UNAVAILABLE"->IntegrationAccessAudit.Reason.AUDIT;case "PAYLOAD_TOO_LARGE"->IntegrationAccessAudit.Reason.OVERSIZED;default->IntegrationAccessAudit.Reason.READ;});
            when(service.overview(any(IntegrationInvocation.class),anyLong(),any())).thenThrow(fault);
            var result=send("tools/call","get_case_overview",Map.of("caseId",100)).path("result");
            assertTrue(result.path("isError").asBoolean());assertFalse(result.has("structuredContent"));publicMetadataOnly(result);
            var value=McpTransportConfiguration.mapper().getJsonMapper().readTree(result.path("content").get(0).path("text").asText());
            assertEquals(3,value.size());assertEquals(code,value.path("code").asText());UUID.fromString(value.path("correlationId").asText());
            assertTrue(code.equals("THROTTLED")?value.path("retryAfterSeconds").asInt()==1:value.path("retryAfterSeconds").isNull());
        }
    }
    private static void publicMetadataOnly(tools.jackson.databind.JsonNode result) {
        var meta=result.path("_meta");assertEquals(1,meta.size());
        assertEquals("shale-mcp",meta.path(McpSchema.MetaKeys.SERVER_INFO).path("name").asText());
        assertEquals("test",meta.path(McpSchema.MetaKeys.SERVER_INFO).path("version").asText());
    }
}
