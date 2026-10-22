package com.shale.server.oauth;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.shale.data.security.ai.AiIntegrationSecurityService.Registration;
import java.math.BigInteger;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.server.authorization.client.*;
import org.springframework.security.oauth2.server.authorization.settings.*;

/**
 * Pure adapter over the Phase 3B.1 registration/redirect/key fields. Not a Spring bean or global lookup.
 * A trusted persistence adapter must supply a fresh confined snapshot for one pre-resolved owner.
 * No SQL grants/projection are widened here; operational loading and live rechecks precede 3B.2B/C.
 */
public final class TenantBoundOAuthClientRepository implements RegisteredClientRepository {
    public enum ClientType { PUBLIC, CONFIDENTIAL }
    public record Owner(int shaleClientId, UUID integrationId) {
        public Owner {
            if (shaleClientId <= 0) throw invalid();
            Objects.requireNonNull(integrationId);
        }
    }
    public record PinnedKey(Owner owner, UUID credentialId, RSAPublicKey publicKey,
            String algorithm, Instant validFrom, Instant expiresAt, Instant revokedAt) {}
    public record Snapshot(Owner owner, Registration registration, ClientType clientType,
            String issuer, String resource, List<String> redirects, List<PinnedKey> keys) {
        public Snapshot {
            redirects = List.copyOf(redirects); keys = List.copyOf(keys);
        }
    }

    private final RegisteredClient client;
    private final Instant expiresAt;
    private final List<PinnedKey> keys;
    private final Clock clock;

    public TenantBoundOAuthClientRepository(Owner expectedOwner, Snapshot snapshot, McpOAuthProperties properties, Clock clock) {
        Objects.requireNonNull(expectedOwner); Objects.requireNonNull(snapshot); this.clock = Objects.requireNonNull(clock);
        var registration = Objects.requireNonNull(snapshot.registration());
        if (!expectedOwner.equals(snapshot.owner()) || !expectedOwner.integrationId().equals(registration.integrationId()) ||
                !properties.issuer().equals(snapshot.issuer()) || !properties.resource().equals(snapshot.resource()) ||
                snapshot.clientType() == null || !"ACTIVE".equals(registration.state()) ||
                registration.expiresAt() == null || !registration.expiresAt().isAfter(clock.instant()) ||
                registration.clientId() == null || !registration.clientId().matches("[A-Za-z0-9_-]{1,64}") ||
                registration.ceiling().isEmpty() || snapshot.redirects().isEmpty() || snapshot.redirects().size() > 5 ||
                new HashSet<>(snapshot.redirects()).size() != snapshot.redirects().size()) throw invalid();
        boolean confidential = snapshot.clientType() == ClientType.CONFIDENTIAL;
        snapshot.redirects().forEach(value -> OAuthRedirectPolicy.registered(value, confidential));
        if (!confidential && !snapshot.keys().isEmpty()) throw invalid();
        Set<UUID> keyIds = new HashSet<>();
        for (PinnedKey key : snapshot.keys()) {
            if (!expectedOwner.equals(key.owner()) || key.credentialId() == null || !keyIds.add(key.credentialId()) ||
                    !"RS256".equals(key.algorithm()) || key.publicKey() == null ||
                    key.publicKey().getModulus().bitLength() < 2048 || key.publicKey().getModulus().bitLength() > 4096 ||
                    !BigInteger.valueOf(65537).equals(key.publicKey().getPublicExponent()) ||
                    key.validFrom() == null || key.expiresAt() == null || !key.expiresAt().isAfter(key.validFrom())) throw invalid();
        }
        keys = snapshot.keys();
        if (confidential && liveKeys().isEmpty()) throw invalid();
        var builder = RegisteredClient.withId(expectedOwner.shaleClientId() + ":" + expectedOwner.integrationId())
                .clientId(registration.clientId())
                .clientAuthenticationMethod(confidential ? ClientAuthenticationMethod.PRIVATE_KEY_JWT : ClientAuthenticationMethod.NONE)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .clientSettings(ClientSettings.builder().requireProofKey(true).requireAuthorizationConsent(true)
                        .tokenEndpointAuthenticationSigningAlgorithm(SignatureAlgorithm.RS256).build())
                .tokenSettings(TokenSettings.builder().accessTokenFormat(OAuth2TokenFormat.REFERENCE)
                        .accessTokenTimeToLive(Duration.ofMinutes(5)).build());
        snapshot.redirects().forEach(builder::redirectUri);
        registration.ceiling().forEach(scope -> builder.scope(scope.wireValue()));
        client = builder.build(); expiresAt = registration.expiresAt();
    }

    /** Only local public JWKs. No URL, private key, client secret, network fetch or decoder fallback. */
    public JWKSet pinnedPublicKeys() {
        List<JWK> jwks = liveKeys().stream().map(key -> (JWK) new RSAKey.Builder(key.publicKey())
                .keyID(key.credentialId().toString()).keyUse(KeyUse.SIGNATURE).algorithm(JWSAlgorithm.RS256).build()).toList();
        return new JWKSet(jwks);
    }

    @Override public RegisteredClient findByClientId(String clientId) {
        return active() && client.getClientId().equals(clientId) ? client : null;
    }
    @Override public RegisteredClient findById(String id) {
        return active() && client.getId().equals(id) ? client : null;
    }
    @Override public void save(RegisteredClient client) {
        throw new UnsupportedOperationException("Registration belongs to the audited tenant integration service.");
    }
    private boolean active() {
        return expiresAt.isAfter(clock.instant()) &&
                (client.getClientAuthenticationMethods().contains(ClientAuthenticationMethod.NONE) || !liveKeys().isEmpty());
    }
    private List<PinnedKey> liveKeys() {
        Instant now = clock.instant();
        return keys.stream().filter(key -> key.revokedAt() == null && !key.validFrom().isAfter(now) && key.expiresAt().isAfter(now)).toList();
    }
    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("A current exact tenant/integration OAuth registration is required.");
    }
}
