package com.shale.server.oauth;

import org.springframework.security.oauth2.core.OAuth2Token;
import org.springframework.security.oauth2.server.authorization.*;
import org.springframework.security.oauth2.server.authorization.client.*;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenGenerator;

/** No in-memory fallback, enabled registration, authorization persistence, consent or token generator. */
public final class OAuthIssuanceDisabled implements RegisteredClientRepository {
    public static final OAuth2TokenGenerator<OAuth2Token> TOKENS = context -> null;
    @Override public void save(RegisteredClient client) { throw disabled(); }
    @Override public RegisteredClient findByClientId(String clientId) { return null; }
    @Override public RegisteredClient findById(String id) { return null; }
    public static final OAuth2AuthorizationService AUTHORIZATIONS = new OAuth2AuthorizationService() {
        @Override public void save(OAuth2Authorization authorization) { throw disabled(); }
        @Override public void remove(OAuth2Authorization authorization) { throw disabled(); }
        @Override public OAuth2Authorization findById(String id) { return null; }
        @Override public OAuth2Authorization findByToken(String token, OAuth2TokenType type) { return null; }
    };
    public static final OAuth2AuthorizationConsentService CONSENTS = new OAuth2AuthorizationConsentService() {
        @Override public void save(OAuth2AuthorizationConsent consent) { throw disabled(); }
        @Override public void remove(OAuth2AuthorizationConsent consent) { throw disabled(); }
        @Override public OAuth2AuthorizationConsent findById(String clientId, String principalName) { return null; }
    };
    private static UnsupportedOperationException disabled() {
        return new UnsupportedOperationException("AI OAuth credential lifecycle is not implemented.");
    }
}
