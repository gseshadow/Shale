package com.shale.server.mcp;

import com.shale.server.oauth.McpOAuthSecurityConfiguration;
import com.shale.server.oauth.OAuthMetadataContract;
import org.junit.jupiter.api.Test;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mock.web.*;
import org.springframework.security.web.FilterChainProxy;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class McpFilterIsolationTest {
    @Test void disabledMcpAndOAuthCompositionAreIndependentWithNoCatchAllChain()throws Exception {
        for(boolean oauth:List.of(false,true)) try(var context=new AnnotationConfigWebApplicationContext()) {
            context.setServletContext(new MockServletContext());context.getEnvironment().setActiveProfiles("test");
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("mcp",Map.of(
                    "shale.ai.oauth.public-origin","https://shale.test","shale.ai.oauth.identity","TEST",
                    "shale.ai.oauth.composition-enabled",oauth)));
            context.register(McpOAuthSecurityConfiguration.class,McpTransportConfiguration.class);context.refresh();
            var proxy=context.getBean(FilterChainProxy.class);assertEquals(2,proxy.getFilterChains().size());
            var mcp=proxy.getFilterChains().getFirst();
            assertTrue(mcp.getFilters().stream().noneMatch(f->f.getClass().getSimpleName().matches(
                    "BasicAuthenticationFilter|UsernamePasswordAuthenticationFilter|DefaultLoginPageGeneratingFilter|BearerTokenAuthenticationFilter")));
            for(String path:List.of("/api/auth/login","/api/auth/desktop-session/restore","/api/cases/search","/api/health","/v3/api-docs","/login","/","/oauth/mcp/token",OAuthMetadataContract.AUTHORIZATION_SERVER_PATH))
                assertFalse(mcp.matches(new MockHttpServletRequest("POST",path)),path);
            for(String path:List.of("/mcp",OAuthMetadataContract.PROTECTED_RESOURCE_PATH)) {
                var response=new MockHttpServletResponse();proxy.doFilter(new MockHttpServletRequest("POST",path),response,(r,s)->fail());
                assertEquals(404,response.getStatus());
            }
            for(String path:List.of("/api/health","/api/auth/login","/v3/api-docs","/login","/")) {
                var response=new MockHttpServletResponse();proxy.doFilter(new MockHttpServletRequest("GET",path),response,(r,s)->s.getWriter().write("ordinary"));
                assertEquals("ordinary",response.getContentAsString());
            }
        }
    }
    @Test void canonicalConfigurationRejectsWildcardsAliasesAndProductionHttp() {
        for(String origin:List.of("*","null","https://CLIENT.test","https://client.test/","https://client.test:443","http://public.example"))
            assertThrows(IllegalArgumentException.class,()->new McpTransportProperties(true,Set.of(origin)),origin);
        assertThrows(IllegalArgumentException.class,()->new com.shale.server.oauth.McpOAuthProperties("http://public.example",com.shale.server.oauth.McpOAuthProperties.Identity.PRODUCTION,true));
        assertFalse(new McpTransportProperties(false,null).enabled());
    }
    @Test void transportOnGlobalOffStartsWithoutSqlCredentialsAndDeniesProtectedUse()throws Exception {
        try(var context=new AnnotationConfigWebApplicationContext()) {
            context.setServletContext(new MockServletContext());context.getEnvironment().setActiveProfiles("test");
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("mcp",Map.of(
                    "shale.ai.mcp.enabled",true,"shale.ai.enabled",false,
                    "shale.ai.oauth.public-origin","https://shale.test","shale.ai.oauth.identity","TEST")));
            context.register(McpOAuthSecurityConfiguration.class,McpTransportConfiguration.class);context.refresh();
            assertFalse(context.containsBean("mcpAccessAuthenticator"));
            var request=new MockHttpServletRequest("POST","/mcp");request.setContentType("application/json");
            request.addHeader("Accept","application/json");request.addHeader("Authorization","Bearer "+"a".repeat(43));
            request.addHeader("MCP-Protocol-Version","2026-07-28");request.addHeader("Mcp-Method","server/discover");
            var response=new MockHttpServletResponse();context.getBean(FilterChainProxy.class).doFilter(request,response,(r,s)->fail());
            assertEquals(401,response.getStatus());assertNull(request.getSession(false));
        }
    }
}
