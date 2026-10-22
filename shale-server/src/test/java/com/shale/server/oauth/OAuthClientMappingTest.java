package com.shale.server.oauth;

import com.shale.core.security.ai.AiScope;
import com.shale.data.security.ai.AiIntegrationSecurityService.*;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.server.authorization.authentication.*;
import static com.shale.server.oauth.TenantBoundOAuthClientRepository.*;
import static org.junit.jupiter.api.Assertions.*;

class OAuthClientMappingTest {
    private static final Instant NOW = Instant.parse("2026-10-22T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final Owner OWNER = new Owner(7, UUID.fromString("00000000-0000-0000-0000-000000000007"));
    private static final McpOAuthProperties PROPERTIES = new McpOAuthProperties("https://shale.test", McpOAuthProperties.Identity.TEST, true);
    private static final String CHALLENGE = Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[32]);

    static Snapshot snapshot(ClientType type, List<String> redirects, List<PinnedKey> keys) {
        var registration = new Registration(OWNER.integrationId(), "synthetic-client", AiScope.fromMask(3), "ACTIVE", NOW.plusSeconds(3600), new RowVersion(new byte[8]));
        return new Snapshot(OWNER, registration, type, PROPERTIES.issuer(), PROPERTIES.resource(), redirects, keys);
    }
    static PinnedKey key() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA"); generator.initialize(2048);
        return new PinnedKey(OWNER, UUID.randomUUID(), (RSAPublicKey) generator.generateKeyPair().getPublic(),
                "RS256", NOW.minusSeconds(60), NOW.plusSeconds(600), null);
    }
    static TenantBoundOAuthClientRepository repository(Snapshot snapshot) {
        return new TenantBoundOAuthClientRepository(OWNER, snapshot, PROPERTIES, CLOCK);
    }

    @Test void publicClientIsSecretlessBoundCodeOnlyAndAlwaysRequiresPkceAndConsent() {
        var repository = repository(snapshot(ClientType.PUBLIC, List.of("http://127.0.0.1:3333/callback"), List.of()));
        var client = repository.findByClientId("synthetic-client");
        assertEquals(Set.of(ClientAuthenticationMethod.NONE), client.getClientAuthenticationMethods());
        assertNull(client.getClientSecret());
        assertTrue(client.getClientSettings().isRequireProofKey());
        assertTrue(client.getClientSettings().isRequireAuthorizationConsent());
        assertNull(client.getClientSettings().getJwkSetUrl());
        assertEquals(Set.of(AuthorizationGrantType.AUTHORIZATION_CODE), client.getAuthorizationGrantTypes());
        assertEquals(Set.of("cases.search", "cases.overview.read"), client.getScopes());
        assertEquals("reference", client.getTokenSettings().getAccessTokenFormat().getValue());
        assertNull(repository.findByClientId("SYNTHETIC-client"));
        assertNull(repository.findById("8:" + OWNER.integrationId()));
        assertSame(client, repository.findById(client.getId()));
        assertThrows(UnsupportedOperationException.class, () -> repository.save(client));
        assertTrue(repository.pinnedPublicKeys().isEmpty());
    }

    @Test void confidentialProfilePinsOnlyLocalRsaRs256WithoutSecretOrRemoteJwks() throws Exception {
        var pinned = key();
        var repository = repository(snapshot(ClientType.CONFIDENTIAL, List.of("https://client.test/callback"), List.of(pinned)));
        var client = repository.findByClientId("synthetic-client");
        assertEquals(Set.of(ClientAuthenticationMethod.PRIVATE_KEY_JWT), client.getClientAuthenticationMethods());
        assertEquals(SignatureAlgorithm.RS256, client.getClientSettings().getTokenEndpointAuthenticationSigningAlgorithm());
        assertNull(client.getClientSecret()); assertNull(client.getClientSettings().getJwkSetUrl());
        assertTrue(client.getClientSettings().isRequireProofKey());
        var jwk = repository.pinnedPublicKeys().getKeys().getFirst();
        assertFalse(jwk.isPrivate()); assertEquals("RS256", jwk.getAlgorithm().getName());
        assertEquals(pinned.credentialId().toString(), jwk.getKeyID());
    }

    @Test void mappingRejectsCrossOwnerIdentityStaleRegistrationAndUnpinnedProfiles() throws Exception {
        var snapshot = snapshot(ClientType.PUBLIC, List.of("https://client.test/callback"), List.of());
        assertThrows(IllegalArgumentException.class, () -> new TenantBoundOAuthClientRepository(new Owner(8, OWNER.integrationId()), snapshot, PROPERTIES, CLOCK));
        assertThrows(IllegalArgumentException.class, () -> new TenantBoundOAuthClientRepository(new Owner(7, UUID.randomUUID()), snapshot, PROPERTIES, CLOCK));
        assertThrows(IllegalArgumentException.class, () -> new TenantBoundOAuthClientRepository(OWNER, snapshot,
                new McpOAuthProperties("https://other.test", McpOAuthProperties.Identity.TEST, true), CLOCK));
        for (String state : List.of("SUSPENDED", "REVOKED", "unknown")) {
            var old = snapshot.registration();
            var changed = new Registration(old.integrationId(), old.clientId(), old.ceiling(), state, old.expiresAt(), old.rowVersion());
            assertThrows(IllegalArgumentException.class, () -> repository(new Snapshot(OWNER, changed, ClientType.PUBLIC,
                    snapshot.issuer(), snapshot.resource(), snapshot.redirects(), List.of())));
        }
        assertThrows(IllegalArgumentException.class, () -> new TenantBoundOAuthClientRepository(OWNER, snapshot, PROPERTIES,
                Clock.fixed(NOW.plusSeconds(3600), ZoneOffset.UTC)));
        assertThrows(IllegalArgumentException.class, () -> repository(snapshot(ClientType.CONFIDENTIAL, snapshot.redirects(), List.of())));
        assertThrows(IllegalArgumentException.class, () -> repository(snapshot(ClientType.PUBLIC, snapshot.redirects(), List.of(key()))));
        var pinned = key();
        for (var invalid : List.of(
                new PinnedKey(new Owner(8, OWNER.integrationId()), pinned.credentialId(), pinned.publicKey(), "RS256", pinned.validFrom(), pinned.expiresAt(), null),
                new PinnedKey(OWNER, pinned.credentialId(), pinned.publicKey(), "RS512", pinned.validFrom(), pinned.expiresAt(), null),
                new PinnedKey(OWNER, pinned.credentialId(), pinned.publicKey(), "RS256", pinned.validFrom(), pinned.expiresAt(), NOW),
                new PinnedKey(OWNER, pinned.credentialId(), pinned.publicKey(), "RS256", NOW.plusSeconds(1), pinned.expiresAt(), null)))
            assertThrows(IllegalArgumentException.class, () -> repository(snapshot(ClientType.CONFIDENTIAL, snapshot.redirects(), List.of(invalid))));
    }

    @Test void redirectPolicyAllowsExactHostedAndOnlyNativePublicLoopbackPortVariation() {
        for (String callback : List.of("https://client.test/callback", "https://client.test:8443/callback"))
            for (boolean confidential : new boolean[]{true,false}) {
                OAuthRedirectPolicy.validate(callback, Set.of(callback), confidential);
                for (String wrong : List.of(callback + "/", callback + "?next=https://evil.test", callback + "#fragment",
                        callback.replace("client.test", "evil.test"), callback.replace("https", "http")))
                    assertThrows(IllegalArgumentException.class, () -> OAuthRedirectPolicy.validate(wrong, Set.of(callback), confidential), wrong);
            }
        for (String host : List.of("127.0.0.1", "[::1]")) {
            String callback = "http://" + host + ":3333/callback";
            OAuthRedirectPolicy.validate("http://" + host + ":49152/callback", Set.of(callback), false);
            for (String wrong : List.of("http://localhost:49152/callback", "http://127.0.0.2:49152/callback",
                    "http://" + host + ":49152/other", "http://" + host + "/callback"))
                assertThrows(IllegalArgumentException.class, () -> OAuthRedirectPolicy.validate(wrong, Set.of(callback), false));
            assertThrows(IllegalArgumentException.class, () -> OAuthRedirectPolicy.registered(callback, true));
        }
        for (String wrong : List.of("https://*.test/callback", "https://user@client.test/callback", "https://client.test/a/../callback",
                "https://client.test/%2e/callback", "https://client.test", "http://localhost:3333/callback"))
            assertThrows(IllegalArgumentException.class, () -> OAuthRedirectPolicy.registered(wrong, false), wrong);
    }

    @Test void actualFrameworkAuthorizationValidatorEnforcesS256ResourceAndRedirectForBothProfiles() throws Exception {
        for (var type : ClientType.values()) {
            var client = repository(snapshot(type, List.of("https://client.test/callback"), type == ClientType.PUBLIC ? List.of() : List.of(key())))
                    .findByClientId("synthetic-client");
            var policy = new OAuthAuthorizationRequestPolicy(PROPERTIES);
            var parameters = Map.<String,Object>of("code_challenge_method", "S256", "code_challenge", CHALLENGE, "resource", PROPERTIES.resource());
            policy.accept(context(client, "https://client.test/callback", parameters));
            for (var bad : List.of(Map.of("code_challenge_method", "plain"), Map.of("code_challenge_method", "s256"),
                    Map.of("code_challenge", "short"), Map.of("code_challenge", List.of(CHALLENGE, CHALLENGE)),
                    Map.of("code_challenge", CHALLENGE.substring(0,42) + "B"), Map.of("code_challenge_method", List.of("S256", "plain")),
                    Map.of("resource", List.of(PROPERTIES.resource(), PROPERTIES.resource())), Map.of("resource", PROPERTIES.resource() + "/"))) {
                var changed = new HashMap<String,Object>(parameters); changed.putAll(bad);
                assertThrows(OAuth2AuthorizationCodeRequestAuthenticationException.class,
                        () -> policy.accept(context(client, "https://client.test/callback", changed)), type + " " + bad);
            }
            for (String missing : List.of("code_challenge_method", "code_challenge", "resource")) {
                var changed = new HashMap<>(parameters); changed.remove(missing);
                assertThrows(OAuth2AuthorizationCodeRequestAuthenticationException.class,
                        () -> policy.accept(context(client, "https://client.test/callback", changed)));
            }
            var failure = assertThrows(OAuth2AuthorizationCodeRequestAuthenticationException.class,
                    () -> policy.accept(context(client, "https://attacker.test/callback", parameters)));
            assertNull(failure.getAuthorizationCodeRequestAuthentication(), "Invalid callback must never receive an OAuth error redirect");
        }
    }

    private static OAuth2AuthorizationCodeRequestAuthenticationContext context(
            org.springframework.security.oauth2.server.authorization.client.RegisteredClient client, String redirect, Map<String,Object> parameters) {
        var request = new OAuth2AuthorizationCodeRequestAuthenticationToken(PROPERTIES.issuer() + "/authorize", client.getClientId(),
                new org.springframework.security.authentication.AnonymousAuthenticationToken("test", "anonymous",
                                List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ANONYMOUS"))),
                redirect, "state", Set.of("cases.search"), parameters);
        return OAuth2AuthorizationCodeRequestAuthenticationContext.with(request).registeredClient(client).build();
    }
}
