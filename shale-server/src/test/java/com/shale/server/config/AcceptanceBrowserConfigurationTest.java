package com.shale.server.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import static org.junit.jupiter.api.Assertions.*;

class AcceptanceBrowserConfigurationTest {
    @Test void packageCannotRunWithProductionOrMissingOrigins() {
        var config = new AcceptanceBrowserConfiguration();
        assertThrows(IllegalStateException.class, () -> config.acceptanceBrowserAssets(new MockEnvironment()));
        var env = new MockEnvironment().withProperty("shale.ai.oauth.public-origin", AcceptanceBrowserConfiguration.ORIGIN)
            .withProperty("shale.ai.oauth.browser-origin", "https://app.myshale.com");
        assertThrows(IllegalStateException.class, () -> config.acceptanceBrowserAssets(env));
        env.setProperty("shale.ai.oauth.browser-origin", AcceptanceBrowserConfiguration.ORIGIN);
        assertEquals(-110, config.acceptanceBrowserAssets(env).getOrder());
    }
    @Test void fragmentNavigationServesExistingUiWithoutCreatingSession() throws Exception {
        for (String path : java.util.List.of("/login", "/oauth/mcp/authorize")) {
            var request = new MockHttpServletRequest("GET", path); request.addHeader("Accept", "text/html");
            if (path.equals("/login")) request.setQueryString("returnTo=%2Foauth%2Fmcp%2Fauthorize");
            var response = new MockHttpServletResponse();
            new AcceptanceBrowserConfiguration.BrowserAssets("browser-fixture/").doFilter(request, response, (q,s) -> fail("UI navigation reached API"));
            assertEquals(200, response.getStatus());
            assertTrue(response.getContentAsString().contains("acceptance-browser-test"));
            assertEquals("no-store", response.getHeader("Cache-Control"));
            assertEquals("DENY", response.getHeader("X-Frame-Options"));
            assertNull(request.getSession(false));
        }
    }
    @Test void oauthQueryBearerAndMutationsReachOriginalAuthority() throws Exception {
        for (int scenario = 0; scenario < 4; scenario++) {
            var request = new MockHttpServletRequest(scenario == 3 ? "POST" : "GET", "/oauth/mcp/authorize");
            request.addHeader("Accept", scenario == 2 ? "application/json" : "text/html");
            if (scenario == 0) request.setQueryString("client_id=synthetic&state=synthetic");
            if (scenario == 1) request.addHeader("Authorization", "Bearer synthetic");
            var response = new MockHttpServletResponse();
            new AcceptanceBrowserConfiguration.BrowserAssets("browser-fixture/").doFilter(request, response, (q,s) -> s.getWriter().write("authority"));
            assertEquals("authority", response.getContentAsString());
        }
    }
    @Test void apiAndTraversalAreNeverServedAsHtml() throws Exception {
        for (String path : java.util.List.of("/mcp", "/api/auth/login", "/oauth/mcp/token", "/assets/../index.html", "/assets/%2e%2e/index.html")) {
            var request = new MockHttpServletRequest("GET", path); request.addHeader("Accept", "text/html");
            var response = new MockHttpServletResponse();
            new AcceptanceBrowserConfiguration.BrowserAssets("browser-fixture/").doFilter(request, response, (q,s) -> s.getWriter().write("original"));
            assertEquals("original", response.getContentAsString());
        }
    }
    @Test void missingAssetReturns404WithoutFallback() throws Exception {
        var request = new MockHttpServletRequest("GET", "/assets/missing.js");
        var response = new MockHttpServletResponse();
        new AcceptanceBrowserConfiguration.BrowserAssets("browser-fixture/").doFilter(request, response, (q,s) -> fail());
        assertEquals(404, response.getStatus());
    }
}
