package com.shale.data.security.ai;

import com.shale.core.runtime.DbSessionProvider;
import com.shale.core.security.ai.AiScope;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

/** Confined code bootstrap and tenant-bound atomic exchange. No credential or data authority. */
public final class AiOAuthExchangeStore {
    public record Owner(int tenantId, int userId) {
        public Owner { if(tenantId<=0 || userId<=0) throw new SecurityException("Owner unavailable."); }
    }
    /** Server-verified fields only. Never an authentication credential or VerifiedIntegrationContext. */
    public record ValidatedExchange(int tenantId, int actingUserId, UUID integrationId, String clientId,
            UUID grantId, UUID transactionId, UUID authenticatingKeyId, Set<AiScope> approvedScopes,
            String issuer, String resource, Instant absoluteDeadline, UUID correlationId) {
        public ValidatedExchange { approvedScopes=Set.copyOf(approvedScopes); }
        @Override public String toString() { return "ValidatedExchange[redacted]"; }
    }
    private final DbSessionProvider db;
    private final String issuer,resource;
    public AiOAuthExchangeStore(DbSessionProvider db,String issuer,String resource) {
        this.db=Objects.requireNonNull(db); this.issuer=issuer; this.resource=resource;
        AiIntegrationSecurityService.configuredHttps(issuer); AiIntegrationSecurityService.configuredHttps(resource);
    }
    /** Only this operation uses the dedicated restricted bootstrap identity, with NULL context keys. */
    public Optional<Owner> resolve(String code,String client) {
        var rows=AiSql.call(db,"ResolveAiOAuthCodeV1",r->new Owner(r.getInt("ShaleClientId"),r.getInt("UserId")),digestCode(code),client,issuer,resource);
        if(rows.size()>1) throw new SecurityException("Ambiguous code owner.");
        return rows.stream().findFirst();
    }
    /** Use a freshly initialized tenant runtime lease. All checks, replay reservation, consume and audit commit together. */
    public ValidatedExchange consume(String code,String client,String redirect,String verifier,UUID key,
            String jti,Instant assertionIssuedAt,Instant assertionExpiresAt) {
        return consume(null,code,client,redirect,verifier,key,jti,assertionIssuedAt,assertionExpiresAt);
    }
    ValidatedExchange consume(java.sql.Connection connection,String code,String client,String redirect,String verifier,UUID key,
            String jti,Instant assertionIssuedAt,Instant assertionExpiresAt) {
        if(client==null || !client.matches("[A-Za-z0-9_-]{1,64}") || redirect==null || redirect.isEmpty() || redirect.length()>2048)
            throw new SecurityException("Invalid exchange binding.");
        String challenge=challenge(verifier);
        byte[] jtiHash=null;
        if(key!=null) {
            if(jti==null || jti.isBlank() || jti.length()>256) throw new SecurityException("Assertion unavailable.");
            jtiHash=AiSql.hash(jti.getBytes(StandardCharsets.UTF_8));
        } else if(jti!=null || assertionIssuedAt!=null || assertionExpiresAt!=null) throw new SecurityException("Unexpected assertion.");
        AiSql.Mapper<ValidatedExchange> mapper=r->new ValidatedExchange(r.getInt("ShaleClientId"),r.getInt("UserId"),
                AiSql.uuid(r,"IntegrationId"),r.getString("ClientId"),AiSql.uuid(r,"GrantId"),AiSql.uuid(r,"TransactionId"),
                r.getString("CredentialId")==null?null:AiSql.uuid(r,"CredentialId"),AiScope.fromMask(r.getInt("ApprovedScopeMask")),
                r.getString("Issuer"),r.getString("Resource"),AiSql.instant(r,"Deadline"),AiSql.uuid(r,"CorrelationId"));
        Object[] args={digestCode(code),client,issuer,resource,redirect,challenge,AiSql.nullable(key,java.sql.Types.VARCHAR),jtiHash,
                AiSql.nullable(assertionIssuedAt,java.sql.Types.TIMESTAMP),AiSql.nullable(assertionExpiresAt,java.sql.Types.TIMESTAMP),UUID.randomUUID()};
        try { return AiSql.one(connection==null?AiSql.call(db,"AiExchangeOAuthCodeV1",mapper,args):AiSql.call(connection,"AiExchangeOAuthCodeV1",mapper,args)); }
        catch(java.sql.SQLException failure) { throw new IllegalStateException("Exchange failed closed.",failure); }
    }
    public static String challenge(String verifier) {
        if(verifier==null || !verifier.matches("[A-Za-z0-9._~-]{43,128}")) throw new SecurityException("Invalid PKCE verifier.");
        return Base64.getUrlEncoder().withoutPadding().encodeToString(AiSql.hash(verifier.getBytes(StandardCharsets.US_ASCII)));
    }
    private static byte[] digestCode(String code) {
        if(code==null || !code.matches("[A-Za-z0-9_-]{43}")) throw new SecurityException("Invalid code.");
        return AiSql.hash(code.getBytes(StandardCharsets.US_ASCII));
    }
}
