package com.shale.data.security.ai;

import com.shale.core.runtime.DbSessionProvider;
import com.shale.core.security.ai.AiScope;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.spec.RSAPublicKeySpec;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.*;

/** Bounded procedure adapter. Authenticated runtime context owns tenant/user; never a client parameter.
 * No Spring serialization, raw code storage, credential issuance or Case access. */
public final class AiOAuthTransactionStore {
    public record Key(UUID id, RSAPublicKey rsa, String algorithm, Instant validFrom, Instant expiresAt) {}
    public record Client(int tenant, UUID integration, String clientId, String name, String type, Set<AiScope> scopes,
            Instant expiresAt, AiIntegrationSecurityService.RowVersion version, String issuer, String resource,
            Set<String> redirects, List<Key> keys) {
        public Client { scopes=Set.copyOf(scopes); redirects=Set.copyOf(redirects); keys=List.copyOf(keys); }
    }
    public record Intent(UUID id, int tenant, int user, UUID integration, UUID webSession, String clientId,
            String redirect, String challenge, String clientState, Set<AiScope> requested, Instant createdAt) {
        public Intent { requested=Set.copyOf(requested); }
        @Override public String toString() { return "OAuthIntent[redacted]"; }
    }
    /** This receipt is deliberately not a bearer credential or VerifiedIntegrationContext. */
    public record Consumed(UUID transactionId, UUID grantId, Set<AiScope> scopes) {
        public Consumed { scopes=Set.copyOf(scopes); }
    }
    private final DbSessionProvider runtime;
    private final boolean enabled;
    private final String issuer, resource;
    public AiOAuthTransactionStore(DbSessionProvider runtime, boolean enabled, String issuer, String resource) {
        this.runtime=Objects.requireNonNull(runtime); this.enabled=enabled;
        if(enabled) { AiIntegrationSecurityService.configuredHttps(issuer); AiIntegrationSecurityService.configuredHttps(resource); }
        this.issuer=issuer; this.resource=resource;
    }
    private void requireEnabled() { if(!enabled) throw new SecurityException("AI capability is off."); }

    /** Fresh one-client snapshot under authenticated tenant RLS. ClientId is globally unique in SQL.
     * Unknown, foreign, expired or disabled clients fail closed; no client inventory API exists. */
    public Optional<Client> loadClient(String clientId) {
        requireEnabled();
        if(clientId==null || !clientId.matches("[A-Za-z0-9_-]{1,64}")) return Optional.empty();
        record Row(int tenant, UUID integration, String name, String type, Set<AiScope> scopes, Instant expiry,
                AiIntegrationSecurityService.RowVersion version, String issuer, String resource, String redirect, Key key) {}
        var rows=AiSql.call(runtime,"AiLoadOAuthClientV1", r -> {
            Key key=null;
            if(r.getString("CredentialId")!=null) {
                try {
                    var rsa=(RSAPublicKey)KeyFactory.getInstance("RSA").generatePublic(new RSAPublicKeySpec(
                            new BigInteger(1,r.getBytes("Modulus")),BigInteger.valueOf(r.getInt("Exponent"))));
                    key=new Key(AiSql.uuid(r,"CredentialId"),rsa,r.getString("Algorithm"),AiSql.instant(r,"ValidFrom"),AiSql.instant(r,"KeyExpiresAt"));
                } catch(java.security.GeneralSecurityException invalid) { throw new java.sql.SQLException("Invalid pinned RSA key."); }
            }
            return new Row(r.getInt("ShaleClientId"),AiSql.uuid(r,"IntegrationId"),r.getString("Name"),r.getString("ClientType"),
                    AiScope.fromMask(r.getInt("ScopeMask")),AiSql.instant(r,"ExpiresAt"),new AiIntegrationSecurityService.RowVersion(r.getBytes("RowVer")),
                    r.getString("Issuer"),r.getString("Resource"),r.getString("RedirectUri"),key);
        },clientId,issuer,resource);
        if(rows.isEmpty()) return Optional.empty();
        var first=rows.getFirst(); var redirects=new LinkedHashSet<String>(); var keys=new LinkedHashMap<UUID,Key>();
        if(rows.size()>10) throw new SecurityException("Ambiguous OAuth registration.");
        for(var row:rows) {
            if(row.tenant()!=first.tenant() || !row.integration().equals(first.integration())) throw new SecurityException("Ambiguous OAuth owner.");
            redirects.add(row.redirect()); if(row.key()!=null) keys.put(row.key().id(),row.key());
        }
        return Optional.of(new Client(first.tenant(),first.integration(),clientId,first.name(),first.type(),first.scopes(),first.expiry(),first.version(),
                first.issuer(),first.resource(),redirects,List.copyOf(keys.values())));
    }
    public void begin(UUID id, UUID session, String client, String redirect, String challenge, String state, Set<AiScope> scopes, String nonce) {
        requireEnabled();
        if(state==null || state.isEmpty() || state.length()>512) throw new IllegalArgumentException("Bounded OAuth state required.");
        AiSql.call(runtime,"AiBeginOAuthV1",r->true,id,session,client,issuer,resource,redirect,challenge,state,mask(scopes),digest(nonce));
    }
    public Optional<Intent> findIntent(String nonce, UUID session) {
        requireEnabled();
        var rows=AiSql.call(runtime,"AiFindOAuthIntentV1",r->new Intent(AiSql.uuid(r,"TransactionId"),r.getInt("ShaleClientId"),r.getInt("UserId"),
                AiSql.uuid(r,"IntegrationId"),AiSql.uuid(r,"WebSessionId"),r.getString("ClientId"),r.getString("RedirectUri"),r.getString("Challenge"),
                r.getString("ClientState"),AiScope.fromMask(r.getInt("RequestedScopeMask")),AiSql.instant(r,"CreatedAt")),digest(nonce),session);
        if(rows.size()>1) throw new SecurityException("Ambiguous OAuth transaction.");
        return rows.stream().findFirst();
    }
    public void approve(UUID id, UUID session, Set<AiScope> scopes) {
        requireEnabled(); AiSql.call(runtime,"AiApproveOAuthV1",r->true,id,session,mask(scopes));
    }
    public void complete(UUID id, UUID session, Set<AiScope> scopes, String code) {
        requireEnabled(); AiSql.call(runtime,"AiCompleteOAuthV1",r->true,id,session,mask(scopes),digest(code),UUID.randomUUID());
    }
    public void deny(UUID id, UUID session) {
        requireEnabled(); AiSql.call(runtime,"AiDenyOAuthV1",r->true,id,session);
    }
    /** Original trusted-WEB consume seam retained for the 3B.2B contract. No credential is issued.
     * HTTP exchange uses AiOAuthExchangeStore after separate client authentication and confined bootstrap.
     * Verifier is hashed locally and never persisted/logged; SQL atomically consumes only the exact binding. */
    public Consumed consume(String code, UUID webSession, String client, String redirect, String verifier, String expectedIssuer, String expectedResource) {
        requireEnabled();
        if(client==null || !client.matches("[A-Za-z0-9_-]{1,64}") || redirect==null || redirect.length()>2048) throw new SecurityException("Invalid code binding.");
        java.net.URI.create(redirect);
        if(!Objects.equals(issuer,expectedIssuer) || !Objects.equals(resource,expectedResource) || verifier==null || !verifier.matches("[A-Za-z0-9._~-]{43,128}"))
            throw new SecurityException("Invalid code binding.");
        String challenge=Base64.getUrlEncoder().withoutPadding().encodeToString(AiSql.hash(verifier.getBytes(StandardCharsets.US_ASCII)));
        return AiSql.one(AiSql.call(runtime,"AiConsumeOAuthCodeV1",r->new Consumed(AiSql.uuid(r,"TransactionId"),AiSql.uuid(r,"GrantId"),
                AiScope.fromMask(r.getInt("ApprovedScopeMask"))),digest(code),webSession,client,issuer,resource,redirect,challenge));
    }
    private static int mask(Set<AiScope> scopes) { int mask=AiScope.mask(scopes); if(mask==0) throw new IllegalArgumentException("Scopes required."); return mask; }
    private static byte[] digest(String value) {
        if(value==null || !value.matches("[A-Za-z0-9_-]{43,128}={0,2}")) throw new IllegalArgumentException("Opaque credential required.");
        return AiSql.hash(value.getBytes(StandardCharsets.US_ASCII));
    }
}
