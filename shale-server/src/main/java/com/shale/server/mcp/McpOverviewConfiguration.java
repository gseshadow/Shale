package com.shale.server.mcp;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;

/** Same confined handler under isolated-test or explicitly authorized production composition. */
@Configuration(proxyBeanMethods=false)
@ConditionalOnProperty(name={"shale.ai.mcp.enabled","shale.ai.enabled","shale.ai.mcp.get-case-overview-enabled"},havingValue="true")
public class McpOverviewConfiguration {
    @Bean McpOverviewHandler mcpOverviewHandler(McpCaseCompositionPolicy policy,McpAiCaseData data) {
        policy.requireHandler("shale.ai.mcp.get-case-overview-enabled");
        data.ready(System.nanoTime()+10_000_000_000L);
        return new McpOverviewHandler(data::reader);
    }
}
