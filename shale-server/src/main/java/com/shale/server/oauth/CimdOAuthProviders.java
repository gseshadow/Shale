package com.shale.server.oauth;

import jakarta.servlet.http.HttpServletRequest;
import java.util.function.Supplier;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.server.authorization.authentication.*;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;

/** Maintained AS providers for the exact external namespace; manual providers retain their own path. */
final class CimdOAuthProviders {
    private static final String EXCHANGE=CimdOAuthProviders.class.getName()+".exchange",REVOKE=CimdOAuthProviders.class.getName()+".revoke";
    private final CimdOAuthLifecycle lifecycle;
    private final Supplier<HttpServletRequest> request;
    CimdOAuthProviders(CimdOAuthLifecycle lifecycle,Supplier<HttpServletRequest> request){this.lifecycle=lifecycle;this.request=request;}
    static boolean external(String client) { return client!=null&&(client.startsWith("https://")||client.startsWith("dcr.")); }
    private static String method(HttpServletRequest req,OAuth2ClientAuthenticationToken converted) {
        if(!req.getParameter("client_id").equals(converted.getPrincipal()))throw new SecurityException();
        if(req.getParameter("client_assertion")!=null) {
            if(!"urn:ietf:params:oauth:client-assertion-type:jwt-bearer".equals(req.getParameter("client_assertion_type"))||
                    !new ClientAuthenticationMethod("urn:ietf:params:oauth:client-assertion-type:jwt-bearer").equals(converted.getClientAuthenticationMethod()))throw new SecurityException();
            return "private_key_jwt";
        }
        if(req.getParameter("client_assertion_type")!=null||!ClientAuthenticationMethod.NONE.equals(converted.getClientAuthenticationMethod()))throw new SecurityException();
        return "none";
    }
    AuthenticationProvider clients() { return new AuthenticationProvider() {
        @Override public boolean supports(Class<?> type){return OAuth2ClientAuthenticationToken.class.isAssignableFrom(type);}
        @Override public Authentication authenticate(Authentication input) {
            var req=request.get();if(!external(req.getParameter("client_id")))return null;
            try {
                var identifier=new OAuthApplicationIdentifier(req.getParameter("client_id"));
                String method=method(req,(OAuth2ClientAuthenticationToken)input);
                var registered=RegisteredClient.withId(identifier.source().name().toLowerCase(java.util.Locale.ROOT)+":"+req.getParameter("client_id")).clientId(req.getParameter("client_id"))
                        .clientAuthenticationMethod(new ClientAuthenticationMethod(method)).authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                        .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN);
                if(OAuthRevocation.matches(req)) {
                    var state=lifecycle.authenticateRevocation(req.getParameter("client_id"),method,req.getParameter("client_assertion"));
                    state.snapshot().redirects().forEach(registered::redirectUri);
                    req.setAttribute(REVOKE,state);
                } else {
                    boolean refresh="refresh_token".equals(req.getParameter("grant_type"));
                    var state=lifecycle.authenticateExchange(req.getParameter("client_id"),req.getParameter(refresh?"refresh_token":"code"),refresh?"REFRESH":"CODE",method,req.getParameter("client_assertion"));
                    state.snapshot().redirects().forEach(registered::redirectUri);req.setAttribute(EXCHANGE,state);
                }
                return new OAuth2ClientAuthenticationToken(registered.build(),new ClientAuthenticationMethod(method),null);
            } catch(SecurityException|IllegalArgumentException denied){throw OAuthCodeExchange.error("invalid_client");}
            catch(IllegalStateException unavailable){throw OAuthCodeExchange.error("server_error");}
        }
    }; }
    AuthenticationProvider grants() { return new AuthenticationProvider() {
        @Override public boolean supports(Class<?> type){return OAuth2AuthorizationCodeAuthenticationToken.class.isAssignableFrom(type)||OAuth2RefreshTokenAuthenticationToken.class.isAssignableFrom(type);}
        @Override public Authentication authenticate(Authentication input) {
            var req=request.get();if(!external(req.getParameter("client_id")))return null;
            try {
                var state=(CimdOAuthLifecycle.Exchange)req.getAttribute(EXCHANGE);if(state==null)throw new SecurityException();
                var result="refresh_token".equals(req.getParameter("grant_type"))?lifecycle.refresh(state,req.getParameter("refresh_token")):
                        lifecycle.redeem(state,req.getParameter("code"),req.getParameter("redirect_uri"),req.getParameter("code_verifier"));
                var principal=(OAuth2ClientAuthenticationToken)input.getPrincipal();
                var scopes=result.scopes().stream().map(com.shale.core.security.ai.AiScope::wireValue).collect(java.util.stream.Collectors.toSet());
                return new OAuth2AccessTokenAuthenticationToken(principal.getRegisteredClient(),principal,
                        new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER,result.accessToken(),result.issuedAt(),result.accessExpiresAt(),scopes),
                        new OAuth2RefreshToken(result.refreshToken(),result.issuedAt()));
            } catch(SecurityException|IllegalArgumentException|IllegalStateException denied){throw OAuthCodeExchange.error("invalid_grant");}
            finally {req.removeAttribute(EXCHANGE);}
        }
    }; }
    AuthenticationProvider revocations() { return new AuthenticationProvider() {
        @Override public boolean supports(Class<?> type){return OAuth2TokenRevocationAuthenticationToken.class.isAssignableFrom(type);}
        @Override public Authentication authenticate(Authentication input) {
            var req=request.get();if(!external(req.getParameter("client_id")))return null;
            try {
                var state=(CimdOAuthLifecycle.Revocation)req.getAttribute(REVOKE);if(state==null)throw new SecurityException();
                lifecycle.revoke(state,req.getParameter("token"));input.setAuthenticated(true);return input;
            } catch(SecurityException|IllegalArgumentException denied){throw OAuthCodeExchange.error("invalid_client");}
            catch(IllegalStateException unavailable){throw OAuthCodeExchange.error("server_error");}
            finally {req.removeAttribute(REVOKE);}
        }
    }; }
}
