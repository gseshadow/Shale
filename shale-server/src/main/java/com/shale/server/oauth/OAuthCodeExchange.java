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

/** Maintained AS client/grant provider seams. Successful SQL validation deliberately terminates without issuance. */
final class OAuthCodeExchange {
    private static final String STATE=OAuthCodeExchange.class.getName()+".state";
    private final AiOAuthExchangeStore bootstrap;
    private final RuntimeConnectionProvider connections;
    private final McpOAuthProperties properties;
    private final Supplier<HttpServletRequest> request;
    private record State(AiOAuthTransactionStore.Client client,DbSessionProvider runtime,PinnedClientAssertion.Proof proof) {
        @Override public String toString() { return "ExchangeState[redacted]"; }
    }
    OAuthCodeExchange(DbSessionProvider bootstrap,RuntimeConnectionProvider connections,McpOAuthProperties properties,Supplier<HttpServletRequest> request) {
        this.bootstrap=new AiOAuthExchangeStore(bootstrap,properties.issuer(),properties.resource());
        this.connections=connections; this.properties=properties; this.request=request;
    }
    AuthenticationProvider clients() { return new AuthenticationProvider() {
        @Override public boolean supports(Class<?> type) { return OAuth2ClientAuthenticationToken.class.isAssignableFrom(type); }
        @Override public Authentication authenticate(Authentication input) {
            try {
                var req=request.get(); validateRequest(req,properties);
                var owner=bootstrap.resolve(req.getParameter("code"),req.getParameter("client_id")).orElseThrow(()->new SecurityException("Code unavailable."));
                DbSessionProvider runtime=()-> {
                    try { return connections.openConnection(new ServerPrincipal(owner.userId(),owner.tenantId(),null)); }
                    catch(java.sql.SQLException unavailable) { throw new IllegalStateException("Exchange runtime unavailable."); }
                };
                var client=new AiOAuthTransactionStore(runtime,true,properties.issuer(),properties.resource()).loadClient(req.getParameter("client_id")).orElseThrow(()->new SecurityException("Client unavailable."));
                if(client.tenant()!=owner.tenantId()) throw new SecurityException("Owner unavailable.");
                boolean confidential="CONFIDENTIAL".equals(client.type());
                var method=confidential?ClientAuthenticationMethod.PRIVATE_KEY_JWT:ClientAuthenticationMethod.NONE;
                var supplied=(OAuth2ClientAuthenticationToken)input;
                var converterMethod=confidential ? new ClientAuthenticationMethod("urn:ietf:params:oauth:client-assertion-type:jwt-bearer") : ClientAuthenticationMethod.NONE;
                if(!converterMethod.equals(supplied.getClientAuthenticationMethod()) || !client.clientId().equals(supplied.getPrincipal())) throw new SecurityException("Client unavailable.");
                PinnedClientAssertion.Proof proof=null;
                if(confidential) {
                    if(!"urn:ietf:params:oauth:client-assertion-type:jwt-bearer".equals(req.getParameter("client_assertion_type"))) throw new SecurityException("Assertion required.");
                    proof=PinnedClientAssertion.verify(req.getParameter("client_assertion"),client,properties.issuer()+"/token",Clock.systemUTC());
                } else if(req.getParameter("client_assertion")!=null || req.getParameter("client_assertion_type")!=null) throw new SecurityException("Unexpected assertion.");
                req.setAttribute(STATE,new State(client,runtime,proof));
                // Only immutable client identifier/auth method/scopes; no raw assertion survives client authentication.
                var registered=RegisteredClient.withId(client.tenant()+":"+client.integration()).clientId(client.clientId())
                        .clientAuthenticationMethod(method).authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE);
                client.scopes().forEach(s->registered.scope(s.wireValue()));
                client.redirects().forEach(registered::redirectUri);
                return new OAuth2ClientAuthenticationToken(registered.build(),method,null);
            } catch(SecurityException | IllegalArgumentException denied) { throw error("invalid_client"); }
            catch(IllegalStateException unavailable) { throw error("server_error"); }
        }
    }; }
    AuthenticationProvider codes() { return new AuthenticationProvider() {
        @Override public boolean supports(Class<?> type) { return OAuth2AuthorizationCodeAuthenticationToken.class.isAssignableFrom(type); }
        @Override public Authentication authenticate(Authentication input) {
            var req=request.get();
            try {
                var state=(State)req.getAttribute(STATE);
                if(state==null) throw new SecurityException("Client required.");
                var p=state.proof();
                var result=new AiOAuthExchangeStore(state.runtime(),properties.issuer(),properties.resource()).consume(req.getParameter("code"),state.client().clientId(),
                        req.getParameter("redirect_uri"),req.getParameter("code_verifier"),p==null?null:p.keyId(),p==null?null:p.jti(),p==null?null:p.issuedAt(),p==null?null:p.expiresAt());
                // Internal immutable receipt intentionally has no protocol serialization or data authorization path.
                Objects.requireNonNull(result);
            } catch(SecurityException | IllegalArgumentException denied) { throw error("invalid_grant"); }
            catch(IllegalStateException deniedOrUnavailable) { throw error("invalid_grant"); }
            finally { req.removeAttribute(STATE); }
            throw new OAuth2AuthenticationException(new OAuth2Error("server_error","Credential issuance is disabled in this phase.",null));
        }
    }; }
    static void validateRequest(HttpServletRequest req,McpOAuthProperties properties) {
        if(!"POST".equals(req.getMethod()) || req.getContentType()==null ||
                !req.getContentType().split(";",2)[0].equalsIgnoreCase("application/x-www-form-urlencoded") ||
                req.getHeader("Authorization")!=null || req.getQueryString()!=null || req.getContentLengthLong()>16384) throw error("invalid_request");
        if(!"authorization_code".equals(req.getParameter("grant_type"))) throw error("unsupported_grant_type");
        var allowed=Set.of("grant_type","client_id","code","redirect_uri","resource","code_verifier","client_assertion_type","client_assertion");
        for(var entry:req.getParameterMap().entrySet()) {
            if(!allowed.contains(entry.getKey()) || entry.getValue().length!=1 || entry.getValue()[0]==null || entry.getValue()[0].isEmpty() ||
                    entry.getValue()[0].length()>(entry.getKey().equals("client_assertion")?8192:2048)) throw error("invalid_request");
        }
        for(String required:List.of("client_id","code","redirect_uri","resource","code_verifier")) if(req.getParameter(required)==null) throw error("invalid_request");
        if(!req.getParameter("client_id").matches("[A-Za-z0-9_-]{1,64}") || !req.getParameter("code").matches("[A-Za-z0-9_-]{43}")) throw error("invalid_request");
        properties.validateResources(List.of(req.getParameter("resource")));
        AiOAuthExchangeStore.challenge(req.getParameter("code_verifier"));
    }
    static OAuth2AuthenticationException error(String code) { return new OAuth2AuthenticationException(new OAuth2Error(code)); }
}
