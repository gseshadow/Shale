package com.shale.data.security.ai;

import com.shale.core.runtime.DbSessionProvider;
import com.shale.core.security.ai.AiScope;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.sql.*;
import java.time.Instant;
import java.util.*;

/** Credential issuance/refresh only. No access-token presentation or verified resource context. */
public final class AiOAuthCredentialStore {
    private static final SecureRandom RANDOM=new SecureRandom();
    private final DbSessionProvider db;
    private final String issuer,resource;
    public AiOAuthCredentialStore(DbSessionProvider db,String issuer,String resource) {
        this.db=Objects.requireNonNull(db);this.issuer=issuer;this.resource=resource;
        AiIntegrationSecurityService.configuredHttps(issuer);AiIntegrationSecurityService.configuredHttps(resource);
    }
    public record Tokens(String accessToken,String refreshToken,Instant issuedAt,Instant accessExpiresAt,Set<AiScope> scopes) {
        public Tokens { scopes=Set.copyOf(scopes); }
        @Override public String toString() { return "OAuthCredentials[redacted]"; }
    }
    /** The bootstrap identity resolves REFRESH only, including consumed history for reuse detection. */
    public Optional<AiOAuthExchangeStore.Owner> resolveRefresh(String token,String client) {
        var rows=AiSql.call(db,"ResolveAiRefreshV1",r->new AiOAuthExchangeStore.Owner(r.getInt("ShaleClientId"),r.getInt("UserId")),digest(token),client,issuer,resource);
        if(rows.size()>1)throw new SecurityException("Refresh unavailable.");
        return rows.stream().findFirst();
    }
    /** One transaction owns validation, code/JTI consumption, the verified receipt, family creation and audit. */
    public Tokens redeem(String code,String client,String redirect,String verifier,UUID key,String jti,Instant issued,Instant expires) {
        Tokens result;
        try(Connection c=db.requireConnection()) {
            if(!c.getAutoCommit())throw new IllegalStateException("Fresh issuance lease required.");
            c.setAutoCommit(false);
            try {
                var receipt=new AiOAuthExchangeStore(db,issuer,resource).consume(c,code,client,redirect,verifier,key,jti,issued,expires);
                result=issue(c,receipt);
                c.commit();
            } catch(RuntimeException | SQLException failure) { c.rollback();throw failure; }
        } catch(SQLException failure) {throw new IllegalStateException("Credential issuance failed closed.",failure);}
        return result; // Includes successful lease close; never return secrets on an uncertain commit/close.
    }
    /** Only a ValidatedExchange can enter initial issuance. SQL revalidates its immutable durable lineage. */
    private Tokens issue(Connection c,AiOAuthExchangeStore.ValidatedExchange receipt)throws SQLException {
        if(!issuer.equals(receipt.issuer()) || !resource.equals(receipt.resource()))throw new SecurityException("Exchange unavailable.");
        String access=opaque(),refresh=opaque();
        return AiSql.one(AiSql.call(c,"AiIssueCredentialsV1",r->tokens(r,access,refresh),receipt.transactionId(),receipt.clientId(),issuer,resource,
                receipt.grantId(),AiSql.nullable(receipt.authenticatingKeyId(),Types.VARCHAR),AiScope.mask(receipt.approvedScopes()),
                digest(access),digest(refresh),receipt.correlationId()));
    }
    /** Every call requires independently verified client proof. SQL consumes its JTI with rotation/reuse audit. */
    public Tokens refresh(String token,String client,UUID key,String jti,Instant issued,Instant expires) {
        String access=opaque(),refresh=opaque();
        byte[] jtiHash=null;
        if(key!=null) {
            if(jti==null||jti.isBlank()||jti.length()>256)throw new SecurityException("Assertion unavailable.");
            jtiHash=AiSql.hash(jti.getBytes(StandardCharsets.UTF_8));
        } else if(jti!=null||issued!=null||expires!=null)throw new SecurityException("Unexpected assertion.");
        // A reuse result is returned AFTER committing revocation; throwing inside that transaction would undo it.
        var rows=AiSql.call(db,"AiRefreshCredentialsV1",r->r.getBoolean("Denied")?nullResult():tokens(r,access,refresh),
                digest(token),client,issuer,resource,AiSql.nullable(key,Types.VARCHAR),jtiHash,
                AiSql.nullable(issued,Types.TIMESTAMP),AiSql.nullable(expires,Types.TIMESTAMP),digest(access),digest(refresh),UUID.randomUUID());
        Tokens result=AiSql.one(rows);
        if(result.scopes().isEmpty())throw new SecurityException("Refresh unavailable.");
        return result;
    }
    private static Tokens nullResult() {return new Tokens("","",Instant.EPOCH,Instant.EPOCH,Set.of());}
    private static Tokens tokens(ResultSet r,String access,String refresh)throws SQLException {
        return new Tokens(access,refresh,AiSql.instant(r,"IssuedAt"),AiSql.instant(r,"AccessExpiresAt"),AiScope.fromMask(r.getInt("ScopeMask")));
    }
    static String opaque() {byte[] bytes=new byte[32];RANDOM.nextBytes(bytes);return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);}
    static byte[] digest(String token) {
        if(token==null||!token.matches("[A-Za-z0-9_-]{43}"))throw new SecurityException("Credential unavailable.");
        return AiSql.hash(token.getBytes(StandardCharsets.US_ASCII));
    }
}
