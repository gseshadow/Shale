package com.shale.server.mcp;

import com.shale.core.security.ai.AiScope;
import com.shale.data.security.ai.VerifiedIntegrationContext;
import io.modelcontextprotocol.modern.McpSchema;
import io.modelcontextprotocol.modern.server.*;
import reactor.core.publisher.Mono;
import java.util.List;
import java.util.Set;

/** Closed server-defined catalog. Only explicitly activated minimized Case handlers can be callable. */
final class McpCatalog implements McpFeature {
    private final McpSearchHandler search;
    private final McpOverviewHandler overview;
    private final McpSchema.Implementation serverInfo;
    McpCatalog() { this(null); }
    McpCatalog(McpSearchHandler search) { this(search,McpSchema.Implementation.builder("shale-mcp","development").build()); }
    McpCatalog(McpSearchHandler search,McpSchema.Implementation serverInfo) { this(search,null,serverInfo); }
    McpCatalog(McpSearchHandler search,McpOverviewHandler overview,McpSchema.Implementation serverInfo) {
        this.search=search;this.overview=overview;this.serverInfo=serverInfo;
    }
    static List<McpSchema.Tool> visible(VerifiedIntegrationContext authority) {
        return new McpCatalog().tools(authority);
    }
    List<McpSchema.Tool> tools(VerifiedIntegrationContext authority) {
        if (authority == null) throw new SecurityException("ACCESS required.");
        if(authority.credentialId()==null)throw new SecurityException("ACCESS required.");
        var tools=new java.util.ArrayList<McpSchema.Tool>();
        if(search!=null && authority.effectiveScopes().contains(AiScope.CASES_SEARCH))tools.add(McpSearchHandler.TOOL);
        if(overview!=null && authority.effectiveScopes().contains(AiScope.CASES_OVERVIEW_READ))tools.add(McpOverviewHandler.TOOL);
        return List.copyOf(tools);
    }
    @Override public Set<String> methods() { return Set.of(McpSchema.METHOD_TOOLS_LIST, McpSchema.METHOD_TOOLS_CALL); }
    @Override public void capabilities(McpSchema.ServerCapabilities.Builder builder) { builder.tools(false); }
    @Override public Mono<? extends McpAsyncResponse<? extends McpSchema.Result>> handle(McpRequestContext context, Object params) {
        var authority=(VerifiedIntegrationContext)context.transportContext().get(McpGate.AUTHORITY);
        if (context.method().equals(McpSchema.METHOD_TOOLS_CALL)) {
            if(!(params instanceof java.util.Map<?,?> request))
                return Mono.error(io.modelcontextprotocol.modern.McpException.invalidParams("Unknown or disabled tool"));
            Object name=request.get("name");
            if(tools(authority).stream().noneMatch(tool->tool.name().equals(name)))
                return Mono.error(io.modelcontextprotocol.modern.McpException.invalidParams("Unknown or disabled tool"));
            long deadline=(Long)context.transportContext().get(McpGate.DEADLINE);
            var access=(com.shale.data.security.ai.AiCaseDataRequest)context.transportContext().get(McpGate.ACCESS);
            if("search_cases".equals(name))
                return Mono.fromCallable(()->McpAsyncResponse.result(search.call(authority,deadline,context.requestId(),params,serverInfo,access)));
            return Mono.fromCallable(()->McpAsyncResponse.result(overview.call(authority,deadline,context.requestId(),params,serverInfo,access)));
        }
        return Mono.just(McpAsyncResponse.result(McpSchema.ListToolsResult.builder(tools(authority)).ttlMs(0L).build()));
    }
}
