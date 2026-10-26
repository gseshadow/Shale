package com.shale.server.mcp;

import com.shale.data.security.ai.*;
import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.modern.JsonRpc.JSONRPCResponse;
import io.modelcontextprotocol.modern.McpSchema;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.LongFunction;

/** Bounded single-Case Overview adapter. Identity and completed disclosure come exclusively from the integration seam. */
final class McpOverviewHandler {
    static final McpSchema.Tool TOOL=contract();
    private final java.util.function.BiFunction<Long,AiCaseDataRequest,IntegrationCaseReader> services;
    McpOverviewHandler(LongFunction<IntegrationCaseReadService> services) { Objects.requireNonNull(services);this.services=(deadline,request)->services.apply(deadline); }
    McpOverviewHandler(java.util.function.BiFunction<Long,AiCaseDataRequest,IntegrationCaseReader> services) { this.services=Objects.requireNonNull(services); }

    McpSchema.CallToolResult call(VerifiedIntegrationContext authority,long deadline,Object requestId,Object params,McpSchema.Implementation serverInfo) {
        return call(authority,deadline,requestId,params,serverInfo,null);
    }
    McpSchema.CallToolResult call(VerifiedIntegrationContext authority,long deadline,Object requestId,Object params,McpSchema.Implementation serverInfo,AiCaseDataRequest access) {
        var invocation=IntegrationInvocation.overview(authority,deadline);
        try {
            Map<?,?> request=params instanceof Map<?,?> m?m:Map.of();
            Object arguments=request.get("arguments");
            boolean valid=arguments instanceof Map<?,?> && request.get("inputResponses")==null && request.get("requestState")==null
                    && McpJsonDefaults.getSchemaValidator().validate(TOOL.inputSchema(),arguments).valid();
            Object value=valid?((Map<?,?>)arguments).get("caseId"):null;
            // JSON Schema integer admits 1.0; the wire contract deliberately rejects floating tokens.
            Long caseId=(value instanceof Integer || value instanceof Long) ? ((Number)value).longValue():null;
            var mapper=McpTransportConfiguration.mapper();
            DisclosureBuffer buffer=services.apply(deadline,access).overview(invocation,caseId,completed->{
                try {
                    var envelope=new LinkedHashMap<String,Object>(mapper.getJsonMapper().convertValue(success(completed),Map.class));
                    // Modern SDK stamps this fixed public identity on every result. Include its actual bytes.
                    envelope.put("_meta",Map.of(McpSchema.MetaKeys.SERVER_INFO,serverInfo));
                    return mapper.writeValueAsBytes(JSONRPCResponse.result(requestId,envelope)).length;
                }
                catch(java.io.IOException encoding) { throw new IllegalStateException("Bounded response unavailable."); }
            });
            return success(buffer);
        } catch(IntegrationReadException failure) {
            String code=switch(failure.outcome()) {
                case INVALID_INPUT -> "INVALID_INPUT";
                case DENIED -> "FORBIDDEN";
                case THROTTLED -> "THROTTLED";
                default -> switch(failure.reason()) {
                    case CASE_UNAVAILABLE -> "CASE_UNAVAILABLE";
                    case DEADLINE -> "TIMEOUT";
                    case AUDIT -> "AUDIT_UNAVAILABLE";
                    case OVERSIZED -> "PAYLOAD_TOO_LARGE";
                    default -> "UNAVAILABLE";
                };
            };
            return error(code,invocation.correlationId());
        } catch(Exception unavailable) { return error("UNAVAILABLE",invocation.correlationId()); }
    }
    private static McpSchema.CallToolResult success(DisclosureBuffer buffer)throws java.io.IOException {
        var bytes=buffer.bytes();byte[] value=new byte[bytes.remaining()];bytes.get(value);
        String json=new String(value,StandardCharsets.UTF_8);
        Object structured=McpTransportConfiguration.mapper().getJsonMapper().readValue(json,Map.class);
        return McpSchema.CallToolResult.builder().isError(false).structuredContent(structured)
                .addContent(McpSchema.TextContent.builder(json).build()).build();
    }
    private static McpSchema.CallToolResult error(String code,UUID correlation) {
        String json="{\"code\":\""+code+"\",\"correlationId\":\""+correlation+"\",\"retryAfterSeconds\":"+(code.equals("THROTTLED")?"1":"null")+"}";
        return McpSchema.CallToolResult.builder().isError(true).addContent(McpSchema.TextContent.builder(json).build()).build();
    }
    private static McpSchema.Tool contract() {
        try(var overview=McpOverviewHandler.class.getResourceAsStream("/mcp/get-case-overview.json");
            var search=McpOverviewHandler.class.getResourceAsStream("/mcp/search-cases.json")) {
            if(overview==null || search==null)throw new IllegalStateException("Case contracts required.");
            var mapper=McpTransportConfiguration.responseMapper();
            var template=mapper.getJsonMapper().readValue(overview,Map.class);
            var canonical=mapper.getJsonMapper().readValue(search,Map.class);
            // The existing search resource remains the single authority for both shared definitions.
            ((Map<String,Object>)template.get("outputSchema")).put("$defs",((Map<?,?>)canonical.get("outputSchema")).get("$defs"));
            return mapper.readValue(mapper.writeValueAsBytes(template),McpSchema.Tool.class);
        } catch(java.io.IOException unavailable) { throw new ExceptionInInitializerError("Overview contract unavailable."); }
    }
}
