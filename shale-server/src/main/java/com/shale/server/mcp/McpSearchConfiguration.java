package com.shale.server.mcp;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;

/** Same confined handler under isolated-test or explicitly authorized production composition. */
@Configuration(proxyBeanMethods=false)
@ConditionalOnProperty(name={"shale.ai.mcp.enabled","shale.ai.enabled","shale.ai.mcp.search-cases-enabled"},havingValue="true")
public class McpSearchConfiguration {
    McpSearchHandler mcpSearchHandler(McpCaseCompositionPolicy policy,McpAiCaseData data) {
        return mcpSearchHandler(policy,data,null);
    }
    @Bean McpSearchHandler mcpSearchHandler(McpCaseCompositionPolicy policy,McpAiCaseData data,
            org.springframework.beans.factory.ObjectProvider<com.shale.data.security.ai.AiCimdCaseData> cimd) {
        policy.requireHandler("shale.ai.mcp.search-cases-enabled");
        data.ready(System.nanoTime()+10_000_000_000L);
        var external=cimd==null?null:cimd.getIfAvailable();
        return new McpSearchHandler((deadline,request)->external!=null&&external.accepts(request)?external.reader(request,deadline):data.reader(deadline,request));
    }
}
