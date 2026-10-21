package com.shale.data.security.ai;

import com.shale.core.runtime.DbSessionProvider;
import com.shale.core.security.ai.AiScope;
import java.math.BigInteger;
import java.net.URI;
import java.security.SecureRandom;
import java.security.interfaces.RSAPublicKey;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.*;

/** Backend-only controls. Provider MUST derive and stamp both keys from authenticated Shale identity.
 * There are deliberately no caller-supplied tenant/user arguments, endpoints or Case dependencies. */
public final class AiIntegrationSecurityService {
    private final DbSessionProvider runtime;
    private final boolean startupCapability;
    private final String issuer, resource;
    private static final SecureRandom RANDOM = new SecureRandom();

    public AiIntegrationSecurityService(DbSessionProvider runtime) { this(runtime, false, null, null); }
    public AiIntegrationSecurityService(DbSessionProvider runtime, boolean startupCapability, String issuer, String resource) {
        this.runtime = Objects.requireNonNull(runtime); this.startupCapability = startupCapability;
        if (startupCapability) { configuredHttps(issuer); configuredHttps(resource); }
        this.issuer = issuer; this.resource = resource;
    }

    public record RowVersion(byte[] bytes) {
        public RowVersion { if (bytes == null || bytes.length != 8) throw new IllegalArgumentException("RowVer required."); bytes = bytes.clone(); }
        @Override public byte[] bytes() { return bytes.clone(); }
        @Override public String toString() { return "RowVersion[redacted]"; }
    }
    public record Policy(boolean enabled, RowVersion rowVersion) {}
    public record Registration(UUID integrationId, String clientId, Set<AiScope> ceiling, String state, Instant expiresAt, RowVersion rowVersion) {
        public Registration { ceiling = Set.copyOf(ceiling); }
    }
    public record IntegrationState(UUID integrationId, Set<AiScope> ceiling, String state, RowVersion rowVersion) {
        public IntegrationState { ceiling = Set.copyOf(ceiling); }
    }
    public record ClientKey(UUID credentialId, Instant expiresAt, Instant revokedAt, RowVersion rowVersion) {}
    public record Grant(UUID grantId, Set<AiScope> scopes, Instant expiresAt, RowVersion rowVersion) {
        public Grant { scopes = Set.copyOf(scopes); }
    }
    /** CSRF nonce returned once to a future Shale-hosted consent adapter. Never log or redisplay. */
    public record ConsentIntent(String nonce) {
        @Override public String toString() { return "ConsentIntent[redacted]"; }
    }

    public Policy inspectTenantPolicy() {
        return AiSql.one(AiSql.call(runtime, "AiGetTenantPolicyV1", AiIntegrationSecurityService::policy));
    }
    public List<ClientKey> inspectClientKeys(UUID integration) {
        return AiSql.call(runtime, "AiInspectKeysV1", AiIntegrationSecurityService::clientKey, Objects.requireNonNull(integration));
    }

    public Policy setTenantEnabled(boolean enabled, RowVersion expected) {
        return AiSql.one(AiSql.call(runtime, "AiSetTenantPolicyV1", AiIntegrationSecurityService::policy,
                enabled, bytes(expected), UUID.randomUUID()));
    }

    public Registration register(String name, boolean confidential, Set<AiScope> scopes, int days, List<URI> redirects) {
        requireStartup();
        if (name == null || name.isBlank() || name.length() > 100 || days < 1 || days > 90)
            throw new IllegalArgumentException("Invalid AI registration.");
        configuredHttps(issuer); configuredHttps(resource);
        if (redirects == null || redirects.isEmpty() || redirects.size() > 5 || new HashSet<>(redirects).size() != redirects.size())
            throw new IllegalArgumentException("One to five exact redirects required.");
        for (URI uri : redirects) validateRedirect(uri, confidential);
        // URI syntax excludes literal JSON control/quote characters; still escape defensively.
        String json = "[" + String.join(",", redirects.stream().map(uri -> "\"" + uri.toASCIIString().replace("\\", "\\\\").replace("\"", "\\\"") + "\"").toList()) + "]";
        return AiSql.one(AiSql.call(runtime, "AiRegisterIntegrationV1", AiIntegrationSecurityService::registration,
                name, confidential ? "CONFIDENTIAL" : "PUBLIC", issuer, resource, nonempty(scopes), days, json, UUID.randomUUID()));
    }

    public IntegrationState changeIntegration(UUID integration, Set<AiScope> ceiling, boolean revoke, RowVersion expected) {
        Objects.requireNonNull(expected);
        return AiSql.one(AiSql.call(runtime, "AiChangeIntegrationV1", r -> new IntegrationState(
                AiSql.uuid(r, "IntegrationId"), AiScope.fromMask(r.getInt("ScopeMask")), r.getString("Status"), version(r)),
                Objects.requireNonNull(integration), expected.bytes(), AiScope.mask(ceiling), revoke, UUID.randomUUID()));
    }

    public ClientKey rotateClientKey(UUID integration, RSAPublicKey key, int days, RowVersion expectedIntegrationVersion) {
        requireStartup(); Objects.requireNonNull(key); Objects.requireNonNull(expectedIntegrationVersion);
        if (key.getModulus().bitLength() < 2048 || key.getModulus().bitLength() > 4096 ||
                !key.getPublicExponent().equals(BigInteger.valueOf(65537)) || days < 1 || days > 90)
            throw new IllegalArgumentException("Pinned RSA 2048–4096 public key with exponent 65537 required.");
        byte[] modulus = key.getModulus().toByteArray();
        if (modulus[0] == 0) modulus = Arrays.copyOfRange(modulus, 1, modulus.length);
        return AiSql.one(AiSql.call(runtime, "AiRotateClientKeyV1", AiIntegrationSecurityService::clientKey, integration, expectedIntegrationVersion.bytes(), modulus, days, UUID.randomUUID()));
    }

    public RowVersion revokeClientKey(UUID integration, UUID credential, RowVersion expected) {
        return AiSql.one(AiSql.call(runtime, "AiRevokeClientKeyV1", AiIntegrationSecurityService::version,
                Objects.requireNonNull(integration), Objects.requireNonNull(credential), Objects.requireNonNull(expected).bytes(), UUID.randomUUID()));
    }

    public List<Registration> inspectRegistrations() {
        return AiSql.call(runtime, "AiInspectRegistrationsV1", AiIntegrationSecurityService::registration);
    }

    public ConsentIntent beginConsent(UUID integration, Set<AiScope> scopes, int hours) {
        requireStartup();
        if (hours < 1 || hours > 24) throw new IllegalArgumentException("Consent lifetime must be 1–24 hours.");
        byte[] secret = new byte[32]; RANDOM.nextBytes(secret);
        try {
            AiSql.call(runtime, "AiBeginConsentV1", r -> Boolean.TRUE, Objects.requireNonNull(integration),
                    nonempty(scopes), hours, AiSql.hash(secret), UUID.randomUUID());
            return new ConsentIntent(Base64.getUrlEncoder().withoutPadding().encodeToString(secret));
        } finally { Arrays.fill(secret, (byte) 0); }
    }

    public Grant acceptConsent(String nonce) {
        requireStartup();
        if (nonce == null || !nonce.matches("[A-Za-z0-9_-]{43}")) throw new IllegalArgumentException("Invalid consent nonce.");
        byte[] secret = Base64.getUrlDecoder().decode(nonce);
        try {
            return AiSql.one(AiSql.call(runtime, "AiAcceptConsentV1", r -> new Grant(AiSql.uuid(r,"GrantId"),
                    AiScope.fromMask(r.getInt("ScopeMask")), AiSql.instant(r,"ExpiresAt"), version(r)),
                    AiSql.hash(secret), UUID.randomUUID()));
        } finally { Arrays.fill(secret, (byte) 0); }
    }

    public RowVersion revokeGrant(UUID grant, RowVersion expected) {
        return AiSql.one(AiSql.call(runtime, "AiRevokeGrantV1", AiIntegrationSecurityService::version,
                Objects.requireNonNull(grant), Objects.requireNonNull(expected).bytes(), UUID.randomUUID()));
    }
    public RowVersion revokeSession(UUID session, RowVersion expected) {
        return AiSql.one(AiSql.call(runtime, "AiRevokeSessionV1", AiIntegrationSecurityService::version,
                Objects.requireNonNull(session), Objects.requireNonNull(expected).bytes(), UUID.randomUUID()));
    }

    /** A live session gate, not OAuth token validation or permission to invoke a Case read. */
    public VerifiedIntegrationContext verifySession(UUID session, AiScope operation) {
        requireStartup(); Objects.requireNonNull(session); Objects.requireNonNull(operation);
        return AiSql.one(AiSql.call(runtime, "AiVerifySessionV1", r -> new VerifiedIntegrationContext(
                r.getInt("ShaleClientId"), r.getInt("UserId"), AiSql.uuid(r,"IntegrationId"), AiSql.uuid(r,"GrantId"),
                AiSql.uuid(r,"SessionId"), AiScope.fromMask(r.getInt("ScopeMask")), operation), session, operation.bit()));
    }

    public boolean reserve(VerifiedIntegrationContext context) {
        requireStartup(); Objects.requireNonNull(context);
        return AiSql.one(AiSql.call(runtime, "AiReserveQuotaV1", r -> r.getBoolean("Admitted"),
                context.delegatedSessionId(), context.operationScope().bit(), context.invocationId()));
    }
    /** Releases concurrency only. Durable consumed row/call budgets are never refunded. */
    public void release(VerifiedIntegrationContext context) {
        Objects.requireNonNull(context);
        AiSql.call(runtime, "AiReleaseQuotaV1", r -> Boolean.TRUE, context.invocationId());
    }

    private void requireStartup() { if (!startupCapability) throw new SecurityException("AI capability is off."); }
    private static int nonempty(Set<AiScope> scopes) {
        int mask = AiScope.mask(scopes); if (mask == 0) throw new IllegalArgumentException("Consent scopes required."); return mask;
    }
    private static byte[] bytes(RowVersion v) { return v == null ? null : v.bytes(); }
    private static RowVersion version(ResultSet r) throws SQLException { return new RowVersion(r.getBytes("RowVer")); }
    private static Policy policy(ResultSet r) throws SQLException { return new Policy(r.getBoolean("Enabled"), r.getBytes("RowVer") == null ? null : version(r)); }
    private static ClientKey clientKey(ResultSet r) throws SQLException {
        var revoked = com.shale.data.util.UtcJdbcTimestamp.get(r,"RevokedAt");
        return new ClientKey(AiSql.uuid(r,"CredentialId"), AiSql.instant(r,"ExpiresAt"), revoked == null ? null : revoked.toInstant(), version(r));
    }
    private static Registration registration(ResultSet r) throws SQLException {
        return new Registration(AiSql.uuid(r,"IntegrationId"), r.getString("ClientId"), AiScope.fromMask(r.getInt("ScopeMask")),
                r.getString("Status"), AiSql.instant(r,"ExpiresAt"), version(r));
    }
    static void configuredHttps(String value) {
        if (value == null || value.length() > 256) throw new IllegalArgumentException("Configured HTTPS identity required.");
        URI uri = URI.create(value);
        if (!"https".equals(uri.getScheme()) || uri.getHost() == null || uri.getRawUserInfo() != null ||
                uri.getRawFragment() != null || uri.getRawQuery() != null || uri.getPath().isEmpty())
            throw new IllegalArgumentException("Configured HTTPS identity required.");
    }
    private static void validateRedirect(URI uri, boolean confidential) {
        Objects.requireNonNull(uri);
        boolean loopback = !confidential && "http".equals(uri.getScheme()) && uri.getPort() > 0 &&
                ("127.0.0.1".equals(uri.getHost()) || "[::1]".equals(uri.getHost()));
        if ((!"https".equals(uri.getScheme()) && !loopback) || uri.getHost() == null || uri.getRawUserInfo() != null ||
                uri.getRawFragment() != null || uri.getRawQuery() != null || uri.getPath().isEmpty() ||
                uri.toASCIIString().length() > 2048 || uri.toASCIIString().contains("*"))
            throw new IllegalArgumentException("Invalid exact AI redirect.");
    }
}
