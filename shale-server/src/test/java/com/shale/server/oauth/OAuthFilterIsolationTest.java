package com.shale.server.oauth;

import java.time.Clock;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.web.FilterChainProxy;
import com.shale.server.runtime.ServerPrincipal;
import com.shale.server.runtime.ShaleAuthTokenService;
import static org.junit.jupiter.api.Assertions.*;

class OAuthFilterIsolationTest {
    @Test void libraryChainIsNamespaceOnlyAndAllCredentialAndMetadataEndpointsStayBlocked() throws Exception {
        try (var context = new OAuthSecurityTestContext()) {
            FilterChainProxy proxy = context.filter();
            assertEquals(1, proxy.getFilterChains().size(), "A catch-all fallback chain must never be installed");
            var chain = proxy.getFilterChains().getFirst();
            assertTrue(chain.getFilters().stream().anyMatch(filter -> filter.getClass().getSimpleName().equals("OAuth2TokenEndpointFilter")),
                    "The maintained Authorization Server composition must actually initialize");
            assertTrue(chain.getFilters().stream().noneMatch(filter -> filter.getClass().getSimpleName().matches(
                    "BasicAuthenticationFilter|UsernamePasswordAuthenticationFilter|DefaultLoginPageGeneratingFilter|BearerTokenAuthenticationFilter")));

            var tokens = new ShaleAuthTokenService("synthetic-ordinary-shale-jwt-secret-32-bytes", 3600, Clock.systemUTC());
            var principal = new ServerPrincipal(42,7,"synthetic@example.invalid");
            String jwt = tokens.issue(principal);
            String boundJwt = tokens.issueBound(tokens.prepare(principal), java.util.UUID.randomUUID());
            String remembered = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[32]);
            assertTrue(tokens.verifyToken(jwt).isPresent(), "Use a valid ordinary Shale JWT in the isolation test");
            for (String path : List.of("/oauth/mcp", "/oauth/mcp/authorize", "/oauth/mcp/token", "/oauth/mcp/revoke",
                    "/oauth/mcp/introspect", "/oauth/mcp/jwks", "/oauth/mcp/register", "/oauth/mcp/device_authorization",
                    "/oauth/mcp/par", OAuthMetadataContract.AUTHORIZATION_SERVER_PATH,
                    OAuthMetadataContract.AUTHORIZATION_SERVER_PATH + "/", "/oauth/mcp/.well-known/oauth-authorization-server",
                    "/.well-known/oauth-authorization-server")) {
                for (String method : List.of("GET", "POST")) {
                    for (String authority : List.of("Bearer " + jwt, "Bearer " + boundJwt, "Basic c3ludGhldGljOnNlY3JldA==", "Bearer " + remembered)) {
                        var request = request(method,path); request.addHeader("Authorization", authority);
                        request.addHeader("Host", "attacker.invalid"); request.addHeader("Forwarded", "host=attacker.invalid;proto=http");
                        request.addHeader("X-Forwarded-Host", "attacker.invalid"); request.addHeader("X-Forwarded-Proto", "http");
                        request.addParameter("grant_type", "authorization_code"); request.addParameter("code", "synthetic");
                        // A generic Spring session must not become human/client/integration authority either.
                        request.getSession().setAttribute("SPRING_SECURITY_CONTEXT", new org.springframework.security.core.context.SecurityContextImpl(
                                org.springframework.security.authentication.UsernamePasswordAuthenticationToken.authenticated("web", "", List.of())));
                        var response = new MockHttpServletResponse();
                        proxy.doFilter(request, response, (req,res) -> fail("OAuth request escaped foundation gate: " + path));
                        boolean metadata = method.equals("GET") && path.equals(OAuthMetadataContract.AUTHORIZATION_SERVER_PATH);
                        assertEquals(metadata ? 200 : 404, response.getStatus(), path);
                        if (metadata) {
                            var claims = tools.jackson.databind.json.JsonMapper.builder().build().readTree(response.getContentAsString());
                            assertEquals(java.util.Set.of("issuer", "response_types_supported", "grant_types_supported", "token_endpoint_auth_methods_supported"), claims.propertyNames());
                            assertEquals("https://shale.test/oauth/mcp", claims.path("issuer").asText(), "Host/forwarded headers must not change issuer");
                            for (String capability : List.of("response_types_supported", "grant_types_supported", "token_endpoint_auth_methods_supported"))
                                assertTrue(claims.path(capability).isArray() && claims.path(capability).isEmpty(), capability);
                        } else assertTrue(response.getContentAsString().isEmpty(), "No credential response or alternate metadata may escape the gate");
                        assertNull(response.getHeader("WWW-Authenticate")); assertNull(response.getHeader("Location"));
                        assertEquals(0, response.getCookies().length, "No Spring session cookie may be issued");
                    }
                }
            }
            for (String path : List.of("/api/auth/login", "/api/auth/me", "/api/auth/refresh", "/api/auth/logout",
                    "/api/auth/desktop-session", "/api/auth/desktop-session/restore", "/api/cases/search", "/api/health",
                    "/v3/api-docs", "/login", "/mcp", OAuthMetadataContract.PROTECTED_RESOURCE_PATH)) {
                var request = request("POST",path);
                assertFalse(chain.matches(request), path + " must retain existing Shale authority or remain unmounted");
                var response = new MockHttpServletResponse();
                proxy.doFilter(request, response, (req,res) -> res.getWriter().write("existing-handler"));
                assertEquals("existing-handler", response.getContentAsString(), path);
            }
        }
    }
    private static MockHttpServletRequest request(String method, String path) {
        var request = new MockHttpServletRequest(method,path); request.setServletPath(path); return request;
    }
}
