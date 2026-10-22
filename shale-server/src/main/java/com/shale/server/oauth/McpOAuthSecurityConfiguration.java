package com.shale.server.oauth;

import java.io.IOException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.oauth2.server.authorization.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationProvider;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.context.NullSecurityContextRepository;
import org.springframework.security.web.savedrequest.NullRequestCache;
import org.springframework.web.filter.OncePerRequestFilter;

/** The only Spring security chain. Unmatched Shale routes retain their controller/service authority. */
@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
@EnableConfigurationProperties(McpOAuthProperties.class)
public class McpOAuthSecurityConfiguration {
    @Bean
    SecurityFilterChain mcpOAuthFoundation(HttpSecurity http, McpOAuthProperties properties, Environment environment) throws Exception {
        if (properties.identity() != McpOAuthProperties.Identity.PRODUCTION &&
                !environment.acceptsProfiles(Profiles.of("local", "dev", "test")))
            throw new IllegalArgumentException("Nonproduction OAuth identity requires an explicit dev/local/test profile.");
        if (properties.identity() != McpOAuthProperties.Identity.PRODUCTION &&
                environment.acceptsProfiles(Profiles.of("prod", "azure")))
            throw new IllegalArgumentException("Production profile cannot use a development/test OAuth identity.");
        if (properties.publicOrigin() != null && properties.identity() == McpOAuthProperties.Identity.PRODUCTION &&
                environment.acceptsProfiles(Profiles.of("local", "dev", "test")))
            throw new IllegalArgumentException("Development/test profiles must use a separate OAuth identity.");

        http.securityMatcher(request -> {
            String path = request.getRequestURI().substring(request.getContextPath().length());
            return path.equals("/oauth/mcp") || path.startsWith("/oauth/mcp/") ||
                    path.equals("/.well-known/oauth-authorization-server") ||
                    path.startsWith("/.well-known/oauth-authorization-server/");
        });
        http.formLogin(AbstractHttpConfigurer::disable).httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable).csrf(AbstractHttpConfigurer::disable)
                .requestCache(cache -> cache.requestCache(new NullRequestCache()))
                .securityContext(context -> context.securityContextRepository(new NullSecurityContextRepository()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize.anyRequest().denyAll());

        if (properties.compositionEnabled()) {
            var settings = AuthorizationServerSettings.builder().issuer(properties.issuer()).multipleIssuersAllowed(false)
                    .authorizationEndpoint("/oauth/mcp/authorize").tokenEndpoint("/oauth/mcp/token")
                    .tokenRevocationEndpoint("/oauth/mcp/revoke").tokenIntrospectionEndpoint("/oauth/mcp/introspect")
                    .jwkSetEndpoint("/oauth/mcp/jwks").pushedAuthorizationRequestEndpoint("/oauth/mcp/par")
                    .deviceAuthorizationEndpoint("/oauth/mcp/device_authorization")
                    .deviceVerificationEndpoint("/oauth/mcp/device_verification")
                    .clientRegistrationEndpoint("/oauth/mcp/register").build();
            http.with(new OAuth2AuthorizationServerConfigurer(), server -> server
                    .authorizationServerSettings(settings).registeredClientRepository(new OAuthIssuanceDisabled())
                    .authorizationService(OAuthIssuanceDisabled.AUTHORIZATIONS)
                    .authorizationConsentService(OAuthIssuanceDisabled.CONSENTS)
                    .tokenGenerator(OAuthIssuanceDisabled.TOKENS)
                    .clientAuthentication(authentication -> authentication
                            .authenticationConverters(converters -> {
                                converters.clear();
                                converters.add(new org.springframework.security.oauth2.server.authorization.web.authentication.JwtClientAssertionAuthenticationConverter());
                                converters.add(new org.springframework.security.oauth2.server.authorization.web.authentication.PublicClientAuthenticationConverter());
                            })
                            .authenticationProviders(providers -> {
                                providers.removeIf(provider -> !(provider instanceof org.springframework.security.oauth2.server.authorization.authentication.JwtClientAssertionAuthenticationProvider ||
                                        provider instanceof org.springframework.security.oauth2.server.authorization.authentication.PublicClientAuthenticationProvider));
                                providers.forEach(provider -> {
                                    if (provider instanceof org.springframework.security.oauth2.server.authorization.authentication.JwtClientAssertionAuthenticationProvider jwt)
                                        jwt.setJwtDecoderFactory(client -> {
                                            throw new UnsupportedOperationException("Pinned assertion validation/replay lifecycle is deferred.");
                                        });
                                });
                            }))
                    .authorizationEndpoint(endpoint -> endpoint.authenticationProviders(providers -> providers.forEach(provider -> {
                        if (provider instanceof OAuth2AuthorizationCodeRequestAuthenticationProvider code)
                            code.setAuthenticationValidator(new OAuthAuthorizationRequestPolicy(properties));
                    }))));
        }
        // This phase has no enable-issuance switch. This filter precedes context, client auth and every AS filter.
        // Only the exact, opt-in informational metadata response is allowed through this phase boundary.
        http.addFilterBefore(new FoundationGate(properties), SecurityContextHolderFilter.class);
        return http.build();
    }

    private static final class FoundationGate extends OncePerRequestFilter {
        private final McpOAuthProperties properties;
        private final org.springframework.http.converter.json.JacksonJsonHttpMessageConverter metadata =
                new org.springframework.http.converter.json.JacksonJsonHttpMessageConverter();
        private FoundationGate(McpOAuthProperties properties) { this.properties = properties; }
        @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                throws ServletException, IOException {
            response.setHeader("Cache-Control", "no-store");
            if (properties.compositionEnabled() && "GET".equals(request.getMethod()) &&
                    request.getRequestURI().substring(request.getContextPath().length()).equals(OAuthMetadataContract.AUTHORIZATION_SERVER_PATH)) {
                metadata.write(OAuthMetadataContract.authorizationServer(properties), org.springframework.http.MediaType.APPLICATION_JSON,
                        new org.springframework.http.server.ServletServerHttpResponse(response));
                return;
            }
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        }
    }
}
