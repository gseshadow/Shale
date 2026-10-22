package com.shale.server.oauth;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import static org.junit.jupiter.api.Assertions.*;

class OAuthRevocationPolicyTest {
    private MockHttpServletRequest request(String token) {
        var r=new MockHttpServletRequest("POST","/oauth/mcp/revoke");r.setContentType("application/x-www-form-urlencoded");
        r.setParameter("client_id","client");r.setParameter("token",token);return r;
    }
    @Test void malformedCredentialsAndUnknownHintsDoNotBypassAuthenticationOrBecomeExistenceErrors() {
        for(String token:List.of("a".repeat(43),"malformed","not.a.jwt","é","a=")) {
            var r=request(token);r.setParameter("token_type_hint","unknown-future-hint");
            assertDoesNotThrow(()->OAuthRevocation.validateRequest(r),"Presented credential syntax is not an existence signal");
        }
    }
    @Test void rejectsCallerAuthoritySecretsHeadersQueriesDuplicatesAndUnboundedForms() {
        for(String extra:List.of("tenant_id","user_id","integration_id","session_id","resource","scope","client_secret","jwks_uri")) {
            var r=request("opaque");r.setParameter(extra,"sentinel");assertThrows(RuntimeException.class,()->OAuthRevocation.validateRequest(r),extra);
        }
        for(String header:List.of("Basic YTpi","Bearer web","Bearer desktop")) {
            var r=request("opaque");r.addHeader("Authorization",header);assertThrows(RuntimeException.class,()->OAuthRevocation.validateRequest(r));
        }
        for(String field:List.of("client_id","token","client_assertion","token_type_hint")) {
            var r=request("opaque");r.setParameter(field,"a","b");assertThrows(RuntimeException.class,()->OAuthRevocation.validateRequest(r));
        }
        for(String field:List.of("client_id","token")) {
            var r=request("opaque");r.removeParameter(field);assertThrows(RuntimeException.class,()->OAuthRevocation.validateRequest(r));
        }
        var query=request("opaque");query.setQueryString("token=opaque");assertThrows(RuntimeException.class,()->OAuthRevocation.validateRequest(query));
        var oversized=request("a".repeat(2049));assertThrows(RuntimeException.class,()->OAuthRevocation.validateRequest(oversized));
        var json=request("opaque");json.setContentType("application/json");assertThrows(RuntimeException.class,()->OAuthRevocation.validateRequest(json));
        var get=request("opaque");get.setMethod("GET");assertThrows(RuntimeException.class,()->OAuthRevocation.validateRequest(get));
    }
}
