package com.shale.server.mcp;

import com.shale.server.oauth.McpOAuthProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

/** Synthetic-only activation; setting a flag in a production profile fails startup. */
@Configuration(proxyBeanMethods=false)
@ConditionalOnProperty(name={"shale.ai.mcp.enabled","shale.ai.enabled","shale.ai.mcp.get-case-overview-enabled"},havingValue="true")
public class McpOverviewConfiguration {
    @Bean McpOverviewHandler mcpOverviewHandler(Environment environment,McpOAuthProperties identity,McpAiCaseData data) {
        if(!environment.acceptsProfiles(Profiles.of("test")) || identity.identity()!=McpOAuthProperties.Identity.TEST
                || environment.acceptsProfiles(Profiles.of("prod","azure")))
            throw new IllegalStateException("Overview handler requires isolated test configuration.");
        return new McpOverviewHandler(data::reader);
    }
}
