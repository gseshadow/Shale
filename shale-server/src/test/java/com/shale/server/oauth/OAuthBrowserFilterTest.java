package com.shale.server.oauth;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import static org.junit.jupiter.api.Assertions.*;

class OAuthBrowserFilterTest {
    private final McpOAuthProperties properties = new McpOAuthProperties("https://api.test", McpOAuthProperties.Identity.TEST, true);
    private final OAuthBrowserFilter filter = new OAuthBrowserFilter("https://app.test", properties);
    @Test void browserNavigationUsesOnlyConfiguredOriginAndFragmentWithoutCreatingSession() throws Exception {
        var request = new MockHttpServletRequest("GET", "/oauth/mcp/authorize");
        request.addHeader("Accept", "text/html"); request.addHeader("Host", "evil.test");
        request.addHeader("Forwarded", "host=evil.test;proto=http");
        request.setQueryString("client_id=client&state=a%2Bb&redirect_uri=https%3A%2F%2Fclient.test%2Fcb");
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, (q,s) -> fail("Navigation must go to the first-party login surface"));
        assertEquals("https://app.test/oauth/mcp/authorize#" + request.getQueryString(), response.getRedirectedUrl());
        assertNull(request.getSession(false)); assertEquals(0, response.getCookies().length);
        assertEquals("no-referrer", response.getHeader("Referrer-Policy"));
        assertEquals("no-store", response.getHeader("Cache-Control"));
    }
    @Test void jsonRedirectIsDataAndCannotForwardWebAuthorizationToClient() throws Exception {
        var request = new MockHttpServletRequest("POST", "/oauth/mcp/authorize");
        request.addHeader("Accept", "application/json");request.addHeader("Authorization", "Bearer synthetic");
        request.addHeader("Origin", "https://app.test");
        var response = new MockHttpServletResponse();
        filter.doFilter(request,response,(q,s)->((jakarta.servlet.http.HttpServletResponse)s).sendRedirect("https://client.test/cb?code=synthetic&state=original",307));
        assertEquals(200,response.getStatus());assertNull(response.getHeader("Location"));
        assertTrue(response.getContentAsString().contains("https://client.test/cb?code=synthetic&state=original"));
        assertFalse(response.getContentAsString().contains("Bearer"));
        assertEquals("https://app.test",response.getHeader("Access-Control-Allow-Origin"));
        assertNull(response.getHeader("Access-Control-Allow-Credentials"));
    }
    @Test void foreignNullAndDuplicateOriginsFailClosedBeforeHumanOrSql() throws Exception {
        for (String origin : java.util.List.of("https://evil.test", "null", "https://app.test.evil.test")) {
            var request = new MockHttpServletRequest("POST", "/oauth/mcp/authorize");request.addHeader("Origin",origin);
            var response = new MockHttpServletResponse();
            filter.doFilter(request,response,(q,s)->fail("Foreign origin reached authority"));assertEquals(403,response.getStatus());
        }
        var duplicate = new MockHttpServletRequest("GET", "/oauth/mcp/consent");
        duplicate.addHeader("Origin", "https://app.test");duplicate.addHeader("Origin", "https://app.test");
        var response = new MockHttpServletResponse();filter.doFilter(duplicate,response,(q,s)->fail());assertEquals(403,response.getStatus());
    }
    @Test void preflightIsNarrowAndNeverAuthenticatesOrOpensSql() throws Exception {
        for (String method : java.util.List.of("GET", "POST", "DELETE")) {
            var request = new MockHttpServletRequest("OPTIONS", "/oauth/mcp/authorize");
            request.addHeader("Origin","https://app.test");request.addHeader("Access-Control-Request-Method",method);
            request.addHeader("Access-Control-Request-Headers","authorization,content-type");
            var response = new MockHttpServletResponse();filter.doFilter(request,response,(q,s)->fail());
            assertEquals(method.equals("DELETE")?403:204,response.getStatus());
        }
    }
    @Test void cimdApprovalRoutesKeepTheSameOriginAndPreflightBoundary()throws Exception {
        var enabled=new OAuthBrowserFilter("https://app.test",properties,true);
        for(String path:java.util.List.of("/oauth/mcp/onboarding","/oauth/mcp/approval")) {
            var attack=new MockHttpServletRequest("POST",path);attack.addHeader("Origin","https://evil.test");
            var denied=new MockHttpServletResponse();enabled.doFilter(attack,denied,(q,s)->fail("Foreign origin reached tenant approval"));
            assertEquals(403,denied.getStatus());
            var preflight=new MockHttpServletRequest("OPTIONS",path);preflight.addHeader("Origin","https://app.test");
            preflight.addHeader("Access-Control-Request-Method","POST");preflight.addHeader("Access-Control-Request-Headers","authorization,content-type");
            var response=new MockHttpServletResponse();enabled.doFilter(preflight,response,(q,s)->fail("Preflight reached SQL"));
            assertEquals(204,response.getStatus());assertNull(response.getHeader("Access-Control-Allow-Credentials"));
        }
    }
    @Test void missingBrowserConfigurationPreservesBearerOnlySurfaceAndTokenEndpointIsUntouched() throws Exception {
        var request = new MockHttpServletRequest("GET", "/oauth/mcp/authorize");request.addHeader("Accept","text/html");
        var response = new MockHttpServletResponse();
        new OAuthBrowserFilter(null,properties).doFilter(request,response,(q,s)->s.getWriter().write("human-required"));
        assertNull(response.getRedirectedUrl());assertEquals("human-required",response.getContentAsString());
        var token = new MockHttpServletRequest("POST", "/oauth/mcp/token");token.addHeader("Accept","application/json");
        var untouched = new MockHttpServletResponse();filter.doFilter(token,untouched,(q,s)->((jakarta.servlet.http.HttpServletResponse)s).sendRedirect("/unchanged"));
        assertEquals("/unchanged",untouched.getRedirectedUrl());
        for(String origin:java.util.List.of("https://app.test/", "https://app.test/path", "https://app.test?evil", "https://user@app.test", "https://production.example"))
            assertThrows(IllegalArgumentException.class,()->new OAuthBrowserFilter(origin,properties));
    }
}
