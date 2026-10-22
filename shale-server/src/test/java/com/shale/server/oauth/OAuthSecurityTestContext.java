package com.shale.server.oauth;

import java.util.Map;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;

/** Real library composition around existing standalone controller regression tests; no database. */
public final class OAuthSecurityTestContext implements AutoCloseable {
    private final AnnotationConfigWebApplicationContext context = new AnnotationConfigWebApplicationContext();
    public OAuthSecurityTestContext() {
        context.setServletContext(new MockServletContext());
        context.getEnvironment().setActiveProfiles("test");
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("oauth-test", Map.of(
                "shale.ai.oauth.public-origin", "https://shale.test", "shale.ai.oauth.identity", "TEST",
                "shale.ai.oauth.composition-enabled", "true")));
        context.register(McpOAuthSecurityConfiguration.class);
        context.refresh();
    }
    public FilterChainProxy filter() { return context.getBean(FilterChainProxy.class); }
    @Override public void close() { context.close(); }
}
