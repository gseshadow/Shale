package com.shale.server.oauth;

import java.util.Base64;
import java.util.List;
import java.util.function.Consumer;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.server.authorization.authentication.*;

/** Additional validation installed on the maintained authorization-code provider; no code lifecycle here. */
public final class OAuthAuthorizationRequestPolicy implements Consumer<OAuth2AuthorizationCodeRequestAuthenticationContext> {
    private final McpOAuthProperties properties;
    public OAuthAuthorizationRequestPolicy(McpOAuthProperties properties) { this.properties = properties; }

    @Override public void accept(OAuth2AuthorizationCodeRequestAuthenticationContext context) {
        OAuth2AuthorizationCodeRequestAuthenticationToken request = context.getAuthentication();
        var client = context.getRegisteredClient();
        try {
            OAuthRedirectPolicy.validate(request.getRedirectUri(), client.getRedirectUris(),
                    !client.getClientAuthenticationMethods().contains(ClientAuthenticationMethod.NONE));
            var parameters = request.getAdditionalParameters();
            requireS256(parameters.get("code_challenge_method"), parameters.get("code_challenge"));
            // Spring's converter retains repeated additional values as an array/list. Never coerce these.
            Object resource = parameters.get("resource");
            properties.validateResources(resource instanceof String value ? List.of(value) : null);
        } catch (IllegalArgumentException failure) {
            // A failed redirect must not become an error redirect to the caller's URL.
            throw new OAuth2AuthorizationCodeRequestAuthenticationException(
                    new OAuth2Error("invalid_request", "Invalid OAuth request binding.", null), null);
        }
        OAuth2AuthorizationCodeRequestAuthenticationValidator.DEFAULT_SCOPE_VALIDATOR.accept(context);
    }

    public static void requireS256(Object method, Object challenge) {
        if (!"S256".equals(method) || !(challenge instanceof String value) ||
                !value.matches("[A-Za-z0-9_-]{43}") ||
                !Base64.getUrlEncoder().withoutPadding().encodeToString(Base64.getUrlDecoder().decode(value)).equals(value))
            throw new IllegalArgumentException("Canonical S256 PKCE is mandatory for every client.");
    }
}
