package com.shale.server.config;

import java.io.IOException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnResource;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.web.filter.OncePerRequestFilter;

/** Optional, separately packaged browser assets for the isolated acceptance app only. */
@Configuration(proxyBeanMethods = false)
@Profile("azure")
@ConditionalOnResource(resources = "classpath:/acceptance-browser/index.html")
public class AcceptanceBrowserConfiguration {
    static final String ORIGIN = "https://shale-mcp-acceptance.azurewebsites.net";

    @Bean
    FilterRegistrationBean<BrowserAssets> acceptanceBrowserAssets(Environment environment) {
        if (!ORIGIN.equals(environment.getProperty("shale.ai.oauth.public-origin")) ||
            !ORIGIN.equals(environment.getProperty("shale.ai.oauth.browser-origin"))) {
            throw new IllegalStateException("ACCEPTANCE_BROWSER_ORIGIN_REQUIRED");
        }
        var registration = new FilterRegistrationBean<>(new BrowserAssets());
        registration.setOrder(-110); // Serve fragment navigation before the OAuth servlet chain (-100).
        registration.addUrlPatterns("/login", "/oauth/mcp/authorize", "/assets/*");
        return registration;
    }

    static final class BrowserAssets extends OncePerRequestFilter {
        private final String root;
        BrowserAssets() { this("acceptance-browser/"); }
        BrowserAssets(String root) { this.root = root; }
        @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                FilterChain chain) throws ServletException, IOException {
            String path = request.getRequestURI().substring(request.getContextPath().length());
            boolean asset = path.matches("/assets/[A-Za-z0-9_-]+\\.(js|css|svg|png|woff2)");
            boolean page = (path.equals("/login") || path.equals("/oauth/mcp/authorize")) &&
                request.getQueryString() == null && request.getHeader("Accept") != null &&
                request.getHeader("Accept").contains("text/html") && request.getHeader("Authorization") == null;
            if (!request.getMethod().equals("GET") || (!asset && !page)) {
                chain.doFilter(request, response); return;
            }
            var resource = new ClassPathResource(root + (asset ? path.substring(1) : "index.html"));
            if (!resource.exists()) { response.sendError(404); return; }
            response.setHeader("Cache-Control", "no-store");
            response.setHeader("Referrer-Policy", "no-referrer");
            response.setHeader("X-Content-Type-Options", "nosniff");
            response.setHeader("X-Frame-Options", "DENY");
            response.setHeader("Content-Security-Policy", "default-src 'none'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; font-src 'self'; connect-src 'self'; base-uri 'none'; frame-ancestors 'none'; form-action 'self'");
            response.setContentType(!asset ? "text/html;charset=UTF-8" :
                path.endsWith(".js") ? "text/javascript;charset=UTF-8" :
                path.endsWith(".css") ? "text/css;charset=UTF-8" :
                path.endsWith(".svg") ? "image/svg+xml" :
                path.endsWith(".woff2") ? "font/woff2" : "image/png");
            try (var stream = resource.getInputStream()) { stream.transferTo(response.getOutputStream()); }
        }
    }
}
