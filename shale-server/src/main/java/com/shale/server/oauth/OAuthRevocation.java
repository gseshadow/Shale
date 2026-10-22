package com.shale.server.oauth;

import com.shale.core.runtime.DbSessionProvider;
import com.shale.data.security.ai.*;
import com.shale.server.runtime.*;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.util.*;
import java.util.function.Supplier;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.server.authorization.authentication.*;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;

/** RFC 7009 through maintained AS converters/handlers. Never a resource-token validator. */
final class OAuthRevocation {
    private static final String STATE=OAuthRevocation.class.getName()+".state";
    private final DbSessionProvider bootstrap;
    private final RuntimeConnectionProvider connections;
    private final McpOAuthProperties properties;
    private final Supplier<HttpServletRequest> request;
    private record State(DbSessionProvider runtime,String client,PinnedClientAssertion.Proof proof) {
        @Override public String toString() { return "RevocationState[redacted]"; }
    }
    OAuthRevocation(DbSessionProvider bootstrap,RuntimeConnectionProvider connections,McpOAuthProperties properties,Supplier<HttpServletRequest> request) {
        this.bootstrap=bootstrap;this.connections=connections;this.properties=properties;this.request=request;
    }
    static boolean matches(HttpServletRequest request) {
        return request.getRequestURI().substring(request.getContextPath().length()).equals("/oauth/mcp/revoke");
    }
    AuthenticationProvider clients() { return new AuthenticationProvider() {
        @Override public boolean supports(Class<?> type) { return OAuth2ClientAuthenticationToken.class.isAssignableFrom(type); }
        @Override public Authentication authenticate(Authentication input) {
            var req=request.get();if(!matches(req))return null;
            try {
                validateRequest(req);
                var owner=new AiOAuthCredentialStore(bootstrap,properties.issuer(),properties.resource())
                        .resolveRevocation(req.getParameter("token"),req.getParameter("client_id")).orElseThrow(()->new SecurityException("Client unavailable."));
                DbSessionProvider runtime=()-> {
                    try {return connections.openConnection(new ServerPrincipal(owner.userId(),owner.tenantId(),null));}
                    catch(java.sql.SQLException unavailable) {throw new IllegalStateException("Revocation runtime unavailable.");}
                };
                var client=new AiOAuthTransactionStore(runtime,true,properties.issuer(),properties.resource())
                        .loadRevocationClient(req.getParameter("client_id")).orElseThrow(()->new SecurityException("Client unavailable."));
                if(client.tenant()!=owner.tenantId())throw new SecurityException("Client unavailable.");
                boolean confidential="CONFIDENTIAL".equals(client.type());
                var method=confidential?ClientAuthenticationMethod.PRIVATE_KEY_JWT:ClientAuthenticationMethod.NONE;
                var supplied=(OAuth2ClientAuthenticationToken)input;
                var converted=confidential?new ClientAuthenticationMethod("urn:ietf:params:oauth:client-assertion-type:jwt-bearer"):ClientAuthenticationMethod.NONE;
                if(!converted.equals(supplied.getClientAuthenticationMethod())||!client.clientId().equals(supplied.getPrincipal()))throw new SecurityException("Client unavailable.");
                PinnedClientAssertion.Proof proof=null;
                if(confidential) {
                    if(!"urn:ietf:params:oauth:client-assertion-type:jwt-bearer".equals(req.getParameter("client_assertion_type")))throw new SecurityException("Assertion required.");
                    proof=PinnedClientAssertion.verify(req.getParameter("client_assertion"),client,properties.issuer()+"/revoke",Clock.systemUTC());
                } else if(req.getParameter("client_assertion")!=null||req.getParameter("client_assertion_type")!=null)throw new SecurityException("Unexpected assertion.");
                req.setAttribute(STATE,new State(runtime,client.clientId(),proof));
                var registered=RegisteredClient.withId(client.tenant()+":"+client.integration()).clientId(client.clientId())
                        .clientAuthenticationMethod(method).authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE).redirectUri(client.redirects().iterator().next()).build();
                return new OAuth2ClientAuthenticationToken(registered,method,null);
            } catch(SecurityException | IllegalArgumentException denied) {throw OAuthCodeExchange.error("invalid_client");}
            catch(IllegalStateException unavailable) {throw OAuthCodeExchange.error("server_error");}
        }
    }; }
    AuthenticationProvider credentials() { return new AuthenticationProvider() {
        @Override public boolean supports(Class<?> type) { return OAuth2TokenRevocationAuthenticationToken.class.isAssignableFrom(type); }
        @Override public Authentication authenticate(Authentication input) {
            var req=request.get();
            try {
                var state=(State)req.getAttribute(STATE);
                if(state==null)throw new SecurityException("Client required.");
                var proof=state.proof();
                new AiOAuthCredentialStore(state.runtime(),properties.issuer(),properties.resource()).revoke(req.getParameter("token"),state.client(),
                        proof==null?null:proof.keyId(),proof==null?null:proof.jti(),proof==null?null:proof.issuedAt(),proof==null?null:proof.expiresAt());
                // No existence/ownership result is returned or serialized. Hints are advisory only.
                input.setAuthenticated(true);
                return input;
            } catch(SecurityException denied) {throw OAuthCodeExchange.error("invalid_client");}
            catch(IllegalStateException failure) {
                Throwable cause=failure;
                while(cause!=null && !(cause instanceof java.sql.SQLException))cause=cause.getCause();
                if(cause instanceof java.sql.SQLException sql && Set.of(58802,2601,2627).contains(sql.getErrorCode()))throw OAuthCodeExchange.error("invalid_client");
                throw OAuthCodeExchange.error("server_error");
            } finally {req.removeAttribute(STATE);}
        }
    }; }
    static void validateRequest(HttpServletRequest req) {
        if(!"POST".equals(req.getMethod())||req.getContentType()==null||
                !req.getContentType().split(";",2)[0].equalsIgnoreCase("application/x-www-form-urlencoded")||
                req.getHeader("Authorization")!=null||req.getQueryString()!=null||req.getContentLengthLong()>16384)throw OAuthCodeExchange.error("invalid_request");
        var allowed=Set.of("token","token_type_hint","client_id","client_assertion_type","client_assertion");
        for(var entry:req.getParameterMap().entrySet()) {
            if(!allowed.contains(entry.getKey())||entry.getValue().length!=1||entry.getValue()[0]==null||entry.getValue()[0].isBlank()||
                    entry.getValue()[0].length()>(entry.getKey().equals("client_assertion")?8192:2048))throw OAuthCodeExchange.error("invalid_request");
        }
        if(req.getParameter("token")==null||req.getParameter("client_id")==null||!req.getParameter("client_id").matches("[A-Za-z0-9_-]{1,64}"))throw OAuthCodeExchange.error("invalid_request");
    }
}
