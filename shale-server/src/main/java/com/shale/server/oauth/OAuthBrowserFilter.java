package com.shale.server.oauth;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.web.filter.OncePerRequestFilter;

/** Browser transport only. Human authority still comes exclusively from the durable WEB bearer. */
final class OAuthBrowserFilter extends OncePerRequestFilter {
    private final String webOrigin;
    private final boolean cimd;
    OAuthBrowserFilter(String webOrigin, McpOAuthProperties properties) {
        this(webOrigin,properties,false);
    }
    OAuthBrowserFilter(String webOrigin,McpOAuthProperties properties,boolean cimd) {
        this.cimd=cimd;
        // Reuse canonical HTTPS origin and production/test separation rules. Never use Host/Forwarded.
        this.webOrigin = webOrigin == null ? null : new McpOAuthProperties(webOrigin, properties.identity(), false).publicOrigin();
    }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (!path.equals("/oauth/mcp/authorize") && !path.equals("/oauth/mcp/consent") &&
                !(cimd&&(path.equals("/oauth/mcp/onboarding")||path.equals("/oauth/mcp/approval")))) {
            chain.doFilter(request, response); return;
        }
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Referrer-Policy", "no-referrer");
        response.setHeader("X-Frame-Options", "DENY");
        response.setHeader("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'");
        response.addHeader("Vary", "Origin");
        String origin = request.getHeader("Origin");
        if (origin != null && (Collections.list(request.getHeaders("Origin")).size() != 1 || !origin.equals(webOrigin))) {
            response.setStatus(403); return;
        }
        if (origin != null) response.setHeader("Access-Control-Allow-Origin", webOrigin);
        if ("OPTIONS".equals(request.getMethod())) {
            String method = request.getHeader("Access-Control-Request-Method");
            String headers = request.getHeader("Access-Control-Request-Headers");
            if (origin == null || !("GET".equals(method) || "POST".equals(method)) ||
                    (headers != null && java.util.Arrays.stream(headers.split(",")).anyMatch(h ->
                        !java.util.Set.of("authorization", "accept", "content-type").contains(h.trim().toLowerCase(java.util.Locale.ROOT))))) {
                response.setStatus(403); return;
            }
            response.setHeader("Access-Control-Allow-Methods", "GET, POST");
            response.setHeader("Access-Control-Allow-Headers", "Authorization, Accept, Content-Type");
            response.setStatus(204); return;
        }
        String accept = request.getHeader("Accept");
        if (webOrigin != null && "GET".equals(request.getMethod()) && path.equals("/oauth/mcp/authorize") &&
                request.getHeader("Authorization") == null && accept != null && accept.contains("text/html")) {
            String query = request.getQueryString();
            if (query == null || query.length() > 16384 || query.chars().anyMatch(c -> c < 32 || c == 127)) {
                response.setStatus(400); return;
            }
            // Fragment prevents web-host access logs/referrers receiving the request parameters.
            response.sendRedirect(webOrigin + "/oauth/mcp/authorize#" + query); return;
        }
        if (webOrigin != null && "application/json".equals(accept)) {
            // Do not follow AS redirects in fetch: a 307 could forward the WEB bearer to a client.
            chain.doFilter(request, new HttpServletResponseWrapper(response) {
                @Override public void sendRedirect(String location) throws IOException { redirect(location); }
                @Override public void sendRedirect(String location, int status) throws IOException { redirect(location); }
                @Override public void sendRedirect(String location, int status, boolean clearBuffer) throws IOException { redirect(location); }
                private void redirect(String location) throws IOException {
                    resetBuffer(); setStatus(200);
                    new org.springframework.http.converter.json.JacksonJsonHttpMessageConverter().write(Map.of("redirect", location),
                        org.springframework.http.MediaType.APPLICATION_JSON, new org.springframework.http.server.ServletServerHttpResponse(this));
                }
            });
        } else chain.doFilter(request, response);
    }
}
