package com.shale.server.mcp;

import com.shale.core.security.ai.AiScope;
import com.shale.data.security.ai.VerifiedIntegrationContext;
import io.modelcontextprotocol.modern.McpSchema;
import io.modelcontextprotocol.modern.server.*;
import reactor.core.publisher.Mono;
import java.util.List;
import java.util.Set;

/** Closed server-defined catalog. No Case services, repositories, schemas or handlers are wired. */
final class McpCatalog implements McpFeature {
    enum FutureTool {
        SEARCH("search_cases", AiScope.CASES_SEARCH), OVERVIEW("get_case_overview", AiScope.CASES_OVERVIEW_READ);
        final String name;
        final AiScope scope;
        FutureTool(String name, AiScope scope) { this.name=name; this.scope=scope; }
        boolean activated() { return false; }
    }
    static List<McpSchema.Tool> visible(VerifiedIntegrationContext authority) {
        if (authority == null) throw new SecurityException("ACCESS required.");
        for (var tool : FutureTool.values()) {
            if (tool.activated() && authority.effectiveScopes().contains(tool.scope))
                throw new IllegalStateException("Case handlers require a separate implementation review.");
        }
        return List.of();
    }
    @Override public Set<String> methods() { return Set.of(McpSchema.METHOD_TOOLS_LIST, McpSchema.METHOD_TOOLS_CALL); }
    @Override public void capabilities(McpSchema.ServerCapabilities.Builder builder) { builder.tools(false); }
    @Override public Mono<? extends McpAsyncResponse<? extends McpSchema.Result>> handle(McpRequestContext context, Object params) {
        if (context.method().equals(McpSchema.METHOD_TOOLS_CALL))
            return Mono.error(io.modelcontextprotocol.modern.McpException.invalidParams("Unknown or disabled tool"));
        var authority=(VerifiedIntegrationContext)context.transportContext().get(McpGate.AUTHORITY);
        return Mono.just(McpAsyncResponse.result(McpSchema.ListToolsResult.builder(visible(authority)).ttlMs(0L).build()));
    }
}
