package com.shale.server.oauth;

import java.util.List;
import java.util.Map;

/** Deferred RFC 9728 identity contract, deliberately not a controller/discovery route. */
public final class OAuthMetadataContract {
    public static final String AUTHORIZATION_SERVER_PATH = "/.well-known/oauth-authorization-server/oauth/mcp";
    public static final String PROTECTED_RESOURCE_PATH = "/.well-known/oauth-protected-resource/mcp";
    private OAuthMetadataContract() {}

    /** RFC 8414 with no operational grants. Omission would imply RFC default capabilities.
     * The AS operational builder requires endpoints/nonempty grants. Publish only this immutable
     * informational claim allowlist through Spring's JSON converter until the lifecycle exists. */
    public static Map<String, Object> authorizationServer(McpOAuthProperties properties) {
        return Map.of("issuer", properties.issuer(),
                "response_types_supported", List.of(), "grant_types_supported", List.of(),
                "token_endpoint_auth_methods_supported", List.of());
    }

    public static Map<String,Object> authorizationServer(McpOAuthProperties properties,boolean authorizationEnabled) {
        if(!authorizationEnabled) return authorizationServer(properties);
        return Map.of("issuer",properties.issuer(),"authorization_endpoint",properties.issuer()+"/authorize",
                "response_types_supported",List.of("code"),"grant_types_supported",List.of(),
                "token_endpoint_auth_methods_supported",List.of(),"code_challenge_methods_supported",List.of("S256"),
                "scopes_supported",List.of("cases.search","cases.overview.read"),"authorization_response_iss_parameter_supported",true);
    }

    public static Map<String,Object> authorizationServer(McpOAuthProperties properties,boolean authorizationEnabled,boolean exchangeEnabled) {
        if(!exchangeEnabled) return authorizationServer(properties,authorizationEnabled);
        var claims=new java.util.LinkedHashMap<>(authorizationServer(properties,authorizationEnabled));
        claims.put("token_endpoint",properties.issuer()+"/token");
        claims.put("revocation_endpoint",properties.issuer()+"/revoke");
        claims.put("revocation_endpoint_auth_methods_supported",List.of("none","private_key_jwt"));
        claims.put("revocation_endpoint_auth_signing_alg_values_supported",List.of("RS256"));
        claims.put("grant_types_supported",List.of("authorization_code","refresh_token"));
        claims.put("token_endpoint_auth_methods_supported",List.of("none","private_key_jwt"));
        claims.put("token_endpoint_auth_signing_alg_values_supported",List.of("RS256"));
        claims.put("code_challenge_methods_supported",List.of("S256"));
        return Map.copyOf(claims);
    }

    public static Map<String, Object> protectedResource(McpOAuthProperties properties) {
        return Map.of("resource", properties.resource(), "authorization_servers", List.of(properties.issuer()));
    }
}
