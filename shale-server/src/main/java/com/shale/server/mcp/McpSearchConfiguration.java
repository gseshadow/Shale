package com.shale.server.mcp;

import com.shale.core.service.CaseServicePort;
import com.shale.data.config.DataSources;
import com.shale.data.security.ai.IntegrationCaseReadService;
import com.shale.server.oauth.McpOAuthProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

/** Synthetic-only activation; setting a flag in a production profile fails startup. */
@Configuration(proxyBeanMethods=false)
@ConditionalOnProperty(name={"shale.ai.mcp.enabled","shale.ai.enabled","shale.ai.mcp.search-cases-enabled"},havingValue="true")
public class McpSearchConfiguration {
    @Bean McpSearchHandler mcpSearchHandler(Environment environment,McpOAuthProperties identity,DataSources sources,CaseServicePort cases) {
        if(!environment.acceptsProfiles(Profiles.of("test")) || identity.identity()!=McpOAuthProperties.Identity.TEST
                || environment.acceptsProfiles(Profiles.of("prod","azure")))
            throw new IllegalStateException("Search handler requires isolated test configuration.");
        return new McpSearchHandler(deadline->new IntegrationCaseReadService(new McpJdbcDeadline(sources.runtime(),deadline),
                McpJdbcDeadline.failureAudit(sources.runtime()),cases,true));
    }
}
