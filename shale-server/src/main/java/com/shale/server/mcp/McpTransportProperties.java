package com.shale.server.mcp;

import com.shale.server.oauth.McpOAuthProperties;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Independent operator switch. Origins are canonical serialized origins, compared byte for byte. */
@ConfigurationProperties("shale.ai.mcp")
public record McpTransportProperties(@DefaultValue("false") boolean enabled, Set<String> allowedOrigins) {
    public McpTransportProperties {
        allowedOrigins = allowedOrigins == null ? Set.of() : Set.copyOf(allowedOrigins);
        if (allowedOrigins.size() > 8) throw new IllegalArgumentException("At most eight MCP origins.");
        for (String origin : allowedOrigins) {
            // Reuse the reviewed canonical-origin grammar; profile/TLS restrictions follow below.
            var identity = origin.startsWith("https://") ? McpOAuthProperties.Identity.PRODUCTION : McpOAuthProperties.Identity.DEVELOPMENT;
            if (origin.contains("*") || origin.equals("null")) throw new IllegalArgumentException("Exact MCP origins required.");
            try { new McpOAuthProperties(origin, identity, false); }
            catch (IllegalArgumentException invalid) {
                // Test/dev HTTPS origins are canonical too, but never production identities.
                new McpOAuthProperties(origin, McpOAuthProperties.Identity.TEST, false);
            }
        }
    }
    void validate(McpOAuthProperties identity) {
        if (!enabled) return;
        identity.resource();
        if (identity.identity() == McpOAuthProperties.Identity.PRODUCTION && allowedOrigins.stream().anyMatch(o -> !o.startsWith("https://")))
            throw new IllegalArgumentException("Production MCP origins require HTTPS.");
    }
}
