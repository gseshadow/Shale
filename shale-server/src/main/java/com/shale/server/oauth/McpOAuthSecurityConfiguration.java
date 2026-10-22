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
@org.springframework.context.annotation.Import(OAuthCodeBootstrapConfiguration.class)
@EnableConfigurationProperties(McpOAuthProperties.class)
public class McpOAuthSecurityConfiguration {
    @Bean
    SecurityFilterChain mcpOAuthFoundation(HttpSecurity http, McpOAuthProperties properties, Environment environment,
            org.springframework.beans.factory.ObjectProvider<com.shale.server.runtime.ShaleAuthTokenService> tokens,
            org.springframework.beans.factory.ObjectProvider<com.shale.server.runtime.ServerAuthSessionService> auth,
            org.springframework.beans.factory.ObjectProvider<com.shale.server.runtime.DurableSessionStore> sessions,
            org.springframework.beans.factory.ObjectProvider<com.shale.server.runtime.RuntimeConnectionProvider> connections,
            @org.springframework.beans.factory.annotation.Qualifier("oauthCodeBootstrap") org.springframework.beans.factory.ObjectProvider<com.shale.core.runtime.DbSessionProvider> bootstrap,
            org.springframework.beans.factory.ObjectProvider<HttpServletRequest> currentRequest) throws Exception {
        boolean authorizationEnabled=environment.getProperty("shale.ai.oauth.authorization-enabled",Boolean.class,false);
        if(authorizationEnabled && !properties.compositionEnabled()) throw new IllegalArgumentException("OAuth composition required.");
        boolean exchangeEnabled=environment.getProperty("shale.ai.oauth.exchange-enabled",Boolean.class,false);
        if(exchangeEnabled && !properties.compositionEnabled()) throw new IllegalArgumentException("OAuth composition required.");
        var exchange=exchangeEnabled ? new OAuthCodeExchange(bootstrap.getObject(),connections.getObject(),properties,currentRequest::getObject) : null;
        DurableOAuthAuthorization durable;
        ShaleOAuthHuman humans;
        if(authorizationEnabled) {
            humans=new ShaleOAuthHuman(tokens.getObject(),auth.getObject(),sessions.getObject());
            var store=new com.shale.data.security.ai.AiOAuthTransactionStore(()-> {
                var human=ShaleOAuthHuman.current();
                try { return connections.getObject().openConnection(new com.shale.server.runtime.ServerPrincipal(human.user(),human.tenant(),null)); }
                catch(java.sql.SQLException unavailable) { throw new IllegalStateException("OAuth runtime connection unavailable."); }
            },true,properties.issuer(),properties.resource());
            durable=new DurableOAuthAuthorization(store,properties,currentRequest::getObject);
        } else { durable=null; humans=null; }
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
                .authorizeHttpRequests(authorize -> {
                    if(exchangeEnabled) authorize.requestMatchers("/oauth/mcp/token").authenticated();
                    if(authorizationEnabled) authorize.requestMatchers("/oauth/mcp/authorize").authenticated();
                    authorize.anyRequest().denyAll();
                });

        if (properties.compositionEnabled()) {
            var settings = AuthorizationServerSettings.builder().issuer(properties.issuer()).multipleIssuersAllowed(false)
                    .authorizationEndpoint("/oauth/mcp/authorize").tokenEndpoint("/oauth/mcp/token")
                    .tokenRevocationEndpoint("/oauth/mcp/revoke").tokenIntrospectionEndpoint("/oauth/mcp/introspect")
                    .jwkSetEndpoint("/oauth/mcp/jwks").pushedAuthorizationRequestEndpoint("/oauth/mcp/par")
                    .deviceAuthorizationEndpoint("/oauth/mcp/device_authorization")
                    .deviceVerificationEndpoint("/oauth/mcp/device_verification")
                    .clientRegistrationEndpoint("/oauth/mcp/register").build();
            http.with(new OAuth2AuthorizationServerConfigurer(), server -> server
                    .authorizationServerSettings(settings).registeredClientRepository(durable==null ? new OAuthIssuanceDisabled() : durable.clients())
                    .authorizationService(durable==null ? OAuthIssuanceDisabled.AUTHORIZATIONS : durable)
                    .authorizationConsentService(durable==null ? OAuthIssuanceDisabled.CONSENTS : durable.consents())
                    .tokenGenerator(OAuthIssuanceDisabled.TOKENS)
                    .tokenEndpoint(endpoint -> endpoint
                            .accessTokenRequestConverters(converters -> {
                                converters.clear();
                                converters.add(new org.springframework.security.oauth2.server.authorization.web.authentication.OAuth2AuthorizationCodeAuthenticationConverter());
                            })
                            .authenticationProviders(providers -> { providers.clear(); if(exchange!=null) providers.add(exchange.codes()); }))
                    .clientAuthentication(authentication -> authentication
                            .authenticationConverters(converters -> {
                                converters.clear();
                                converters.add(new org.springframework.security.oauth2.server.authorization.web.authentication.JwtClientAssertionAuthenticationConverter());
                                converters.add(new org.springframework.security.oauth2.server.authorization.web.authentication.PublicClientAuthenticationConverter());
                            })
                            .authenticationProviders(providers -> {
                                providers.clear();
                                if(exchange!=null) providers.add(exchange.clients());
                            }))
                    .authorizationEndpoint(endpoint -> endpoint.consentPage("/oauth/mcp/consent")
                            .authorizationResponseHandler((req,res,authentication)-> {
                                var result=(org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationToken)authentication;
                                String target=org.springframework.web.util.UriComponentsBuilder.fromUriString(result.getRedirectUri())
                                        .queryParam("code", "{code}").queryParam("state","{state}").queryParam("iss","{iss}")
                                        .encode().buildAndExpand(result.getAuthorizationCode().getTokenValue(),result.getState(),properties.issuer()).toUriString();
                                new org.springframework.security.web.DefaultRedirectStrategy().sendRedirect(req,res,target);
                            })
                            .errorResponseHandler((req,res,failure)-> {
                                // Only maintained-provider denial of a validated consent transaction redirects.
                                // Invalid requests/state/client substitutions always fail locally.
                                if(durable!=null && failure instanceof org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationException denied &&
                                        "access_denied".equals(denied.getError().getErrorCode()) && denied.getAuthorizationCodeRequestAuthentication()!=null) {
                                    var result=denied.getAuthorizationCodeRequestAuthentication();
                                    var client=durable.findByClientId(result.getClientId());
                                    if(client!=null && result.getRedirectUri()!=null) {
                                        OAuthRedirectPolicy.validate(result.getRedirectUri(),client.getRedirectUris(),
                                                !client.getClientAuthenticationMethods().contains(org.springframework.security.oauth2.core.ClientAuthenticationMethod.NONE));
                                        String target=org.springframework.web.util.UriComponentsBuilder.fromUriString(result.getRedirectUri())
                                                .queryParam("error","access_denied").queryParam("state","{state}").queryParam("iss","{iss}")
                                                .encode().buildAndExpand(result.getState(),properties.issuer()).toUriString();
                                        new org.springframework.security.web.DefaultRedirectStrategy().sendRedirect(req,res,target);return;
                                    }
                                }
                                res.setStatus(400);
                            }).authenticationProviders(providers -> providers.forEach(provider -> {
                        if (provider instanceof OAuth2AuthorizationCodeRequestAuthenticationProvider code) {
                            code.setAuthenticationValidator(new OAuthAuthorizationRequestPolicy(properties));
                            code.setAuthorizationConsentRequired(context -> true);
                            if(durable!=null) code.setAuthorizationCodeGenerator(DurableOAuthAuthorization.CODES);
                        }
                        if(provider instanceof org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationConsentAuthenticationProvider consent && durable!=null)
                            consent.setAuthorizationCodeGenerator(DurableOAuthAuthorization.CODES);
                    }))));
        }
        // The gate precedes AS processing. Explicit exchange validates and consumes only; issuance stays disabled.
        http.addFilterBefore(new FoundationGate(properties,authorizationEnabled,exchangeEnabled), SecurityContextHolderFilter.class);
        if(durable!=null) http.addFilterAfter(new OAuthHumanFilter(humans,durable),SecurityContextHolderFilter.class);
        return http.build();
    }

    private static final class FoundationGate extends OncePerRequestFilter {
        private final McpOAuthProperties properties;
        private final boolean authorizationEnabled,exchangeEnabled;
        private final org.springframework.http.converter.json.JacksonJsonHttpMessageConverter metadata =
                new org.springframework.http.converter.json.JacksonJsonHttpMessageConverter();
        private FoundationGate(McpOAuthProperties properties,boolean authorizationEnabled,boolean exchangeEnabled) { this.properties=properties; this.authorizationEnabled=authorizationEnabled; this.exchangeEnabled=exchangeEnabled; }
        @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                throws ServletException, IOException {
            response.setHeader("Cache-Control", "no-store");
            if (properties.compositionEnabled() && "GET".equals(request.getMethod()) &&
                    request.getRequestURI().substring(request.getContextPath().length()).equals(OAuthMetadataContract.AUTHORIZATION_SERVER_PATH)) {
                metadata.write(OAuthMetadataContract.authorizationServer(properties,authorizationEnabled), org.springframework.http.MediaType.APPLICATION_JSON,
                        new org.springframework.http.server.ServletServerHttpResponse(response));
                return;
            }
            String path=request.getRequestURI().substring(request.getContextPath().length());
            if(exchangeEnabled && path.equals("/oauth/mcp/token") && "POST".equals(request.getMethod())) {
                try { OAuthCodeExchange.validateRequest(request,properties); chain.doFilter(request,response); }
                catch(org.springframework.security.oauth2.core.OAuth2AuthenticationException failure) {
                    response.setStatus(400); response.setContentType("application/json");
                    response.getWriter().write("{\"error\":\""+failure.getError().getErrorCode()+"\"}");
                } catch(SecurityException | IllegalArgumentException invalid) {
                    response.setStatus(400); response.setContentType("application/json"); response.getWriter().write("{\"error\":\"invalid_request\"}");
                }
                return;
            }
            if(authorizationEnabled && ((path.equals("/oauth/mcp/authorize") && (request.getMethod().equals("GET") || request.getMethod().equals("POST"))) ||
                    (path.equals("/oauth/mcp/consent") && request.getMethod().equals("GET")))) {
                response.setHeader("Referrer-Policy","no-referrer");
                chain.doFilter(request,response); return;
            }
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        }
    }
}
