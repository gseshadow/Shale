package com.shale.server.oauth;

import com.shale.core.security.ai.AiScope;
import com.shale.data.security.ai.AiOAuthTransactionStore;
import java.security.Principal;
import java.time.*;
import java.util.*;
import java.util.function.Supplier;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.server.authorization.*;
import org.springframework.security.oauth2.server.authorization.client.*;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenGenerator;

/** Maintained-AS adapters over fixed SQL fields; no Java object serialization or raw credential persistence. */
public final class DurableOAuthAuthorization implements OAuth2AuthorizationService {
    private final AiOAuthTransactionStore store;
    private final McpOAuthProperties properties;
    private final Supplier<HttpServletRequest> request;
    public DurableOAuthAuthorization(AiOAuthTransactionStore store, McpOAuthProperties properties, Supplier<HttpServletRequest> request) {
        this.store=store; this.properties=properties; this.request=request;
    }
    public RegisteredClient findByClientId(String clientId) {
        var human=ShaleOAuthHuman.current();
        return store.loadClient(clientId).map(c->{
            if(c.tenant()!=human.tenant()) throw denied();
            var owner=new TenantBoundOAuthClientRepository.Owner(c.tenant(),c.integration());
            var registration=new com.shale.data.security.ai.AiIntegrationSecurityService.Registration(c.integration(),c.clientId(),c.scopes(),"ACTIVE",c.expiresAt(),c.version());
            var keys=c.keys().stream().map(k->new TenantBoundOAuthClientRepository.PinnedKey(owner,k.id(),k.rsa(),k.algorithm(),k.validFrom(),k.expiresAt(),null)).toList();
            var snapshot=new TenantBoundOAuthClientRepository.Snapshot(owner,registration,TenantBoundOAuthClientRepository.ClientType.valueOf(c.type()),
                    c.issuer(),c.resource(),List.copyOf(c.redirects()),keys);
            return RegisteredClient.from(new TenantBoundOAuthClientRepository(owner,snapshot,properties,Clock.systemUTC()).findByClientId(clientId)).clientName(c.name()).build();
        }).orElse(null);
    }
    public RegisteredClient findClientById(String id) {
        // Framework IDs are server-created owner tuples; never use them to select another tenant.
        String clientId=request.get().getParameter("client_id");
        var client=findByClientId(clientId);
        return client!=null && client.getId().equals(id) ? client : null;
    }
    public void save(RegisteredClient client) { throw new UnsupportedOperationException("Use audited tenant registration."); }
    public RegisteredClientRepository clients() {
        return new RegisteredClientRepository() {
            @Override public RegisteredClient findByClientId(String id) { return DurableOAuthAuthorization.this.findByClientId(id); }
            @Override public RegisteredClient findById(String id) { return findClientById(id); }
            @Override public void save(RegisteredClient client) { DurableOAuthAuthorization.this.save(client); }
        };
    }
    @Override public void save(OAuth2Authorization authorization) {
        var human=ShaleOAuthHuman.current();
        if(!human.getName().equals(authorization.getPrincipalName())) throw denied();
        var code=authorization.getToken(OAuth2AuthorizationCode.class);
        if(code==null) {
            OAuth2AuthorizationRequest original=authorization.getAttribute(OAuth2AuthorizationRequest.class.getName());
            String nonce=authorization.getAttribute("state");
            var client=findByClientId(original.getClientId());
            if(client==null || !client.getId().equals(authorization.getRegisteredClientId())) throw denied();
            OAuthAuthorizationRequestPolicy.requireS256(original.getAdditionalParameters().get("code_challenge_method"),original.getAdditionalParameters().get("code_challenge"));
            store.begin(UUID.fromString(authorization.getId()),human.session(),original.getClientId(),original.getRedirectUri(),
                    (String)original.getAdditionalParameters().get("code_challenge"),original.getState(),scopes(original.getScopes()),nonce);
        } else {
            if(authorization.getAccessToken()!=null || authorization.getRefreshToken()!=null) throw denied();
            store.complete(UUID.fromString(authorization.getId()),human.session(),scopes(authorization.getAuthorizedScopes()),code.getToken().getTokenValue());
        }
    }
    @Override public OAuth2Authorization findByToken(String token, OAuth2TokenType type) {
        if(type==null || !"state".equals(type.getValue())) return null; // Token exchange stays closed.
        var human=ShaleOAuthHuman.current();
        return store.findIntent(token,human.session()).map(intent->{
            if(intent.tenant()!=human.tenant() || intent.user()!=human.user()) throw denied();
            var client=findByClientId(intent.clientId());
            if(client==null) throw denied();
            var original=OAuth2AuthorizationRequest.authorizationCode().authorizationUri(properties.issuer()+"/authorize")
                    .clientId(intent.clientId()).redirectUri(intent.redirect()).state(intent.clientState())
                    .scopes(wire(intent.requested())).additionalParameters(Map.of("resource",properties.resource(),
                            "code_challenge_method","S256","code_challenge",intent.challenge())).build();
            return OAuth2Authorization.withRegisteredClient(client).id(intent.id().toString()).principalName(human.getName())
                    .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                    .attribute("consentExpiresAt",intent.createdAt().plusSeconds(3600).toString())
                    .attribute(Principal.class.getName(),ShaleOAuthHuman.authentication(human))
                    .attribute(OAuth2AuthorizationRequest.class.getName(),original).attribute("state",token).build();
        }).orElse(null);
    }
    @Override public OAuth2Authorization findById(String id) { throw new UnsupportedOperationException("Only confined consent-state lookup is supported."); }
    @Override public void remove(OAuth2Authorization authorization) {
        if(!authorization.getPrincipalName().equals(ShaleOAuthHuman.current().getName())) throw denied();
        store.deny(UUID.fromString(authorization.getId()),ShaleOAuthHuman.current().session());
    }
    public OAuth2AuthorizationConsentService consents() {
        return new OAuth2AuthorizationConsentService() {
            @Override public OAuth2AuthorizationConsent findById(String client, String principal) {
                // Deliberately no accumulated automatic consent: every transaction requires explicit approval.
                if(!principal.equals(ShaleOAuthHuman.current().getName())) throw denied();
                return null;
            }
            @Override public void save(OAuth2AuthorizationConsent consent) {
                var human=ShaleOAuthHuman.current();
                if(!human.getName().equals(consent.getPrincipalName())) throw denied();
                var pending=findByToken(request.get().getParameter("state"),new OAuth2TokenType("state"));
                if(pending==null || !pending.getRegisteredClientId().equals(consent.getRegisteredClientId())) throw denied();
                store.approve(UUID.fromString(pending.getId()),human.session(),scopes(consent.getScopes()));
            }
            @Override public void remove(OAuth2AuthorizationConsent consent) { throw new UnsupportedOperationException("Use self grant revocation."); }
        };
    }
    /** 256-bit CSPRNG; SQL owns exact 60-second lifetime at commit. Not an access-token generator. */
    public static final OAuth2TokenGenerator<OAuth2AuthorizationCode> CODES=context->{
        byte[] bytes=new byte[32]; new java.security.SecureRandom().nextBytes(bytes);
        var now=Instant.now();
        return new OAuth2AuthorizationCode(Base64.getUrlEncoder().withoutPadding().encodeToString(bytes),now,now.plusSeconds(60));
    };
    static Set<AiScope> scopes(Set<String> values) {
        var result=EnumSet.noneOf(AiScope.class);
        for(var value:values) result.add(Arrays.stream(AiScope.values()).filter(s->s.wireValue().equals(value)).findFirst().orElseThrow(DurableOAuthAuthorization::denied));
        if(result.isEmpty()) throw denied();
        return result;
    }
    static Set<String> wire(Set<AiScope> values) { return values.stream().map(AiScope::wireValue).collect(java.util.stream.Collectors.toUnmodifiableSet()); }
    private static SecurityException denied() { return new SecurityException("OAuth binding unavailable."); }
}
