package com.shale.server.mcp;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;

/** Same confined handler under isolated-test or explicitly authorized production composition. */
@Configuration(proxyBeanMethods=false)
@ConditionalOnProperty(name={"shale.ai.mcp.enabled","shale.ai.enabled","shale.ai.mcp.get-case-overview-enabled"},havingValue="true")
public class McpOverviewConfiguration {
    McpOverviewHandler mcpOverviewHandler(McpCaseCompositionPolicy policy,McpAiCaseData data) {
        return mcpOverviewHandler(policy,data,null);
    }
    @Bean McpOverviewHandler mcpOverviewHandler(McpCaseCompositionPolicy policy,McpAiCaseData data,
            org.springframework.beans.factory.ObjectProvider<com.shale.data.security.ai.AiCimdCaseData> cimd) {
        policy.requireHandler("shale.ai.mcp.get-case-overview-enabled");
        data.ready(System.nanoTime()+10_000_000_000L);
        var external=cimd==null?null:cimd.getIfAvailable();
        return new McpOverviewHandler((deadline,request)->external!=null&&external.accepts(request)?external.reader(request,deadline):data.reader(deadline,request));
    }
}
