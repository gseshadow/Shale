package com.shale.server.mcp;

import com.shale.core.security.ai.AiScope;
import com.shale.data.security.ai.VerifiedIntegrationContext;
import io.modelcontextprotocol.modern.McpSchema;
import io.modelcontextprotocol.modern.server.*;
import reactor.core.publisher.Mono;
import java.util.List;
import java.util.Set;

/** Closed server-defined catalog. Only the explicitly activated search handler can be callable. */
final class McpCatalog implements McpFeature {
    private final McpSearchHandler search;
    McpCatalog() { this(null); }
    McpCatalog(McpSearchHandler search) { this.search=search; }
    static List<McpSchema.Tool> visible(VerifiedIntegrationContext authority) {
        return new McpCatalog().tools(authority);
    }
    List<McpSchema.Tool> tools(VerifiedIntegrationContext authority) {
        if (authority == null) throw new SecurityException("ACCESS required.");
        if(authority.credentialId()==null)throw new SecurityException("ACCESS required.");
        return search!=null && authority.effectiveScopes().contains(AiScope.CASES_SEARCH)?List.of(McpSearchHandler.TOOL):List.of();
    }
    @Override public Set<String> methods() { return Set.of(McpSchema.METHOD_TOOLS_LIST, McpSchema.METHOD_TOOLS_CALL); }
    @Override public void capabilities(McpSchema.ServerCapabilities.Builder builder) { builder.tools(false); }
    @Override public Mono<? extends McpAsyncResponse<? extends McpSchema.Result>> handle(McpRequestContext context, Object params) {
        var authority=(VerifiedIntegrationContext)context.transportContext().get(McpGate.AUTHORITY);
        if (context.method().equals(McpSchema.METHOD_TOOLS_CALL)) {
            if(search==null || !(params instanceof java.util.Map<?,?> request) || !"search_cases".equals(request.get("name")) || tools(authority).isEmpty())
                return Mono.error(io.modelcontextprotocol.modern.McpException.invalidParams("Unknown or disabled tool"));
            long deadline=(Long)context.transportContext().get(McpGate.DEADLINE);
            return Mono.fromCallable(()->McpAsyncResponse.result(search.call(authority,deadline,context.requestId(),params)));
        }
        return Mono.just(McpAsyncResponse.result(McpSchema.ListToolsResult.builder(tools(authority)).ttlMs(0L).build()));
    }
}
