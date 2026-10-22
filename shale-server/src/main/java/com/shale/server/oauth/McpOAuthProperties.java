package com.shale.server.oauth;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Operator configuration only. Neither servlet authority nor forwarded headers are inputs. */
@ConfigurationProperties("shale.ai.oauth")
public record McpOAuthProperties(String publicOrigin, @DefaultValue("PRODUCTION") Identity identity,
        @DefaultValue("false") boolean compositionEnabled) {
    public enum Identity { PRODUCTION, DEVELOPMENT, TEST }

    public McpOAuthProperties {
        if (identity == null) identity = Identity.PRODUCTION;
        if (publicOrigin == null) {
            if (compositionEnabled) throw invalid();
        } else {
            URI uri;
            try { uri = URI.create(publicOrigin); } catch (IllegalArgumentException failure) { throw invalid(); }
            String host = uri.getHost();
            if (publicOrigin.length() > 240 || host == null || uri.isOpaque() ||
                    uri.getRawUserInfo() != null || !uri.getRawPath().isEmpty() ||
                    uri.getRawQuery() != null || uri.getRawFragment() != null ||
                    !("https".equals(uri.getScheme()) || "http".equals(uri.getScheme())) ||
                    !publicOrigin.equals(uri.getScheme() + "://" + host.toLowerCase(Locale.ROOT) +
                            (uri.getPort() == -1 ? "" : ":" + uri.getPort())) ||
                    uri.getPort() == 0 || uri.getPort() > 65535 ||
                    ("https".equals(uri.getScheme()) && uri.getPort() == 443) ||
                    ("http".equals(uri.getScheme()) && uri.getPort() == 80) || host.endsWith(".")) throw invalid();
            boolean local = host.equals("localhost") || host.endsWith(".localhost") || host.startsWith("127.") ||
                    host.equals("[::1]") || host.equals("[0:0:0:0:0:0:0:1]") ||
                    host.equals("0.0.0.0") || host.equals("[::]") ||
                    host.endsWith(".test") || host.endsWith(".invalid");
            if (identity == Identity.PRODUCTION ? (!"https".equals(uri.getScheme()) || local) : !local) throw invalid();
        }
    }

    public String issuer() { return requireOrigin() + "/oauth/mcp"; }
    public String resource() { return requireOrigin() + "/mcp"; }

    /** RFC 8707: one exact resource, including cardinality (duplicates are not one resource). */
    public void validateResources(List<String> resources) {
        if (resources == null || resources.size() != 1 || !resource().equals(resources.getFirst()))
            throw new IllegalArgumentException("Exactly the configured MCP resource is required.");
    }

    private String requireOrigin() {
        if (publicOrigin == null) throw new IllegalStateException("OAuth identity is not configured.");
        return publicOrigin;
    }
    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("Canonical OAuth origin and separate production/dev/test identity required.");
    }
}
