package com.shale.server.oauth;

import com.shale.data.security.ai.AiOAuthExchangeStore;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class OAuthCodeExchangePolicyTest {
    private final McpOAuthProperties properties=new McpOAuthProperties("https://shale.test",McpOAuthProperties.Identity.TEST,true);
    MockHttpServletRequest request() {
        var r=new MockHttpServletRequest("POST","/oauth/mcp/token");r.setContentType("application/x-www-form-urlencoded");
        Map.of("grant_type","authorization_code","client_id","client","code","a".repeat(43),"redirect_uri","https://client.test/callback","resource",properties.resource(),"code_verifier","v".repeat(43)).forEach(r::setParameter);
        return r;
    }
    @Test void onlyAuthorizationCodeSingletonFormWithCanonicalResourceAndPkceIsAllowed() {
        assertDoesNotThrow(()->OAuthCodeExchange.validateRequest(request(),properties));
        for(String grant:List.of("password","implicit","client_credentials","refresh_token","urn:ietf:params:oauth:grant-type:token-exchange","urn:ietf:params:oauth:grant-type:device_code","custom")) {
            var r=request();r.setParameter("grant_type",grant);
            assertEquals("unsupported_grant_type",assertThrows(org.springframework.security.oauth2.core.OAuth2AuthenticationException.class,()->OAuthCodeExchange.validateRequest(r,properties)).getError().getErrorCode());
        }
        for(String field:List.of("client_id","code","redirect_uri","resource","code_verifier")) {
            var r=request();r.removeParameter(field);assertThrows(RuntimeException.class,()->OAuthCodeExchange.validateRequest(r,properties),field);
            var duplicate=request();duplicate.setParameter(field,"a","b");assertThrows(RuntimeException.class,()->OAuthCodeExchange.validateRequest(duplicate,properties),field);
        }
        for(String extra:List.of("client_secret","user_id","tenant_id","scope","issuer","code_challenge","code_challenge_method","jku","jwks_uri")) {
            var r=request();r.setParameter(extra,"sentinel");assertThrows(RuntimeException.class,()->OAuthCodeExchange.validateRequest(r,properties),extra);
        }
        for(String resource:List.of(properties.resource()+"/","https://shale.test/api","https://evil.test/mcp")) {
            var r=request();r.setParameter("resource",resource);assertThrows(RuntimeException.class,()->OAuthCodeExchange.validateRequest(r,properties));
        }
        for(String header:List.of("Bearer ordinary-web-jwt","Bearer remembered-desktop","Basic YTpi")) {
            var r=request();r.addHeader("Authorization",header);assertThrows(RuntimeException.class,()->OAuthCodeExchange.validateRequest(r,properties));
        }
        var query=request();query.setQueryString("client_secret=x");assertThrows(RuntimeException.class,()->OAuthCodeExchange.validateRequest(query,properties));
    }
    @Test void s256UsesRfc7636VectorAndRejectsMalformedVerifiers() {
        assertEquals("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM",AiOAuthExchangeStore.challenge("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"));
        for(String v:List.of("","short","x".repeat(42),"x".repeat(129),"!".repeat(43),"é".repeat(43),"x".repeat(43)+"=")) assertThrows(SecurityException.class,()->AiOAuthExchangeStore.challenge(v));
        assertThrows(SecurityException.class,()->AiOAuthExchangeStore.challenge(null));
        assertNotEquals(AiOAuthExchangeStore.challenge("v".repeat(43)),AiOAuthExchangeStore.challenge("w".repeat(43)));
    }
}
