package com.shale.data.security.ai;

import com.shale.core.runtime.DbSessionProvider;
import com.shale.core.security.ai.AiScope;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.sql.*;
import java.time.Instant;
import java.util.*;

/** Unwired versioned lifecycle adapter. Supply independently attested browser/issuer/resolver leases.
 * This class never falls back to a legacy procedure or manufactures tenant context. */
public final class AiCimdOAuthStore implements AiOAuthBrowserStore {
    private final DbSessionProvider db;
    private final String issuer,resource;
    private final AiExternalContract contract;
    public AiCimdOAuthStore(DbSessionProvider db,String issuer,String resource) {
        this(db,issuer,resource,AiExternalContract.CIMD_V1);
    }
    public AiCimdOAuthStore(DbSessionProvider db,String issuer,String resource,AiExternalContract contract) {
        this.contract=Objects.requireNonNull(contract);
        this.db=Objects.requireNonNull(db);this.issuer=issuer;this.resource=resource;
        AiIntegrationSecurityService.configuredHttps(issuer);AiIntegrationSecurityService.configuredHttps(resource);
    }
    public AiExternalContract contract(){return contract;}
    private <T> List<T> call(String procedure,AiSql.Mapper<T> mapper,Object... args) {
        return AiSql.call(db,contract.procedure(procedure),mapper,args);
    }
    @Override public boolean externalIdentity(){return true;}
    public record ClientProof(String method,UUID keyId,String kid,RSAPublicKey rsa,Instant validFrom,Instant expiresAt) {
        @Override public String toString(){return "CimdClientProof[redacted]";}
    }
    @Override public Optional<AiOAuthTransactionStore.Client> loadClient(String id) {
        throw new UnsupportedOperationException("CIMD client resolution requires the authenticated original WEB session.");
    }
    public Optional<AiOAuthTransactionStore.Client> loadClient(String id,UUID web) {
        contract.requireWireNamespace(id);
        var rows=call("AiCimdLoadClientV1",r->new AiOAuthTransactionStore.Client(r.getInt("ShaleClientId"),AiSql.uuid(r,"IntegrationId"),id,
                r.getString("Name"),r.getString("ClientType"),AiScope.fromMask(r.getInt("ScopeMask")),AiSql.instant(r,"ExpiresAt"),
                new AiIntegrationSecurityService.RowVersion(r.getBytes("RowVer")),r.getString("Issuer"),r.getString("Resource"),Set.of(r.getString("RedirectUri")),List.of()),id,issuer,resource,web);
        if(rows.isEmpty())return Optional.empty();
        if(rows.size()>16)throw new SecurityException("Ambiguous CIMD client.");
        var first=rows.getFirst();var redirects=new LinkedHashSet<String>();
        for(var row:rows){if(row.tenant()!=first.tenant()||!row.integration().equals(first.integration()))throw new SecurityException("Ambiguous CIMD owner.");redirects.addAll(row.redirects());}
        return Optional.of(new AiOAuthTransactionStore.Client(first.tenant(),first.integration(),id,first.name(),first.type(),first.scopes(),first.expiresAt(),first.version(),issuer,resource,redirects,List.of()));
    }
    /** Explicit WEB supplier prevents a request parameter from selecting another session. */
    public AiOAuthBrowserStore browser(java.util.function.Supplier<UUID> originalWeb) {
        var delegate=this;
        return new AiOAuthBrowserStore(){
            private void requireWeb(UUID web){if(!Objects.equals(originalWeb.get(),web))throw new SecurityException("Original WEB session required.");}
            @Override public boolean externalIdentity(){return true;}
            @Override public Optional<AiOAuthTransactionStore.Client> loadClient(String id){return delegate.loadClient(id,originalWeb.get());}
            @Override public void begin(UUID id,UUID web,String client,String redirect,String challenge,String state,Set<AiScope> scopes,String nonce){requireWeb(web);delegate.begin(id,web,client,redirect,challenge,state,scopes,nonce);}
            @Override public Optional<AiOAuthTransactionStore.Intent> findIntent(String nonce,UUID web){requireWeb(web);return delegate.findIntent(nonce,web);}
            @Override public void approve(UUID id,UUID web,Set<AiScope> scopes){requireWeb(web);delegate.approve(id,web,scopes);}
            @Override public void complete(UUID id,UUID web,Set<AiScope> scopes,String code){requireWeb(web);delegate.complete(id,web,scopes,code);}
            @Override public void deny(UUID id,UUID web){requireWeb(web);delegate.deny(id,web);}
        };
    }
    @Override public void begin(UUID id,UUID web,String client,String redirect,String challenge,String state,Set<AiScope> scopes,String nonce) {
        contract.requireWireNamespace(client);
        call("AiCimdBeginOAuthV1",r->true,id,web,client,issuer,resource,redirect,challenge,state,AiScope.mask(scopes),intentDigest(nonce));
    }
    @Override public Optional<AiOAuthTransactionStore.Intent> findIntent(String nonce,UUID web) {
        var rows=call("AiCimdFindOAuthIntentV1",r->new AiOAuthTransactionStore.Intent(AiSql.uuid(r,"TransactionId"),r.getInt("ShaleClientId"),r.getInt("UserId"),AiSql.uuid(r,"IntegrationId"),
                AiSql.uuid(r,"WebSessionId"),r.getString("ExternalClientId"),r.getString("RedirectUri"),r.getString("Challenge"),r.getString("ClientState"),AiScope.fromMask(r.getInt("RequestedScopeMask")),AiSql.instant(r,"CreatedAt"),AiSql.instant(r,"GrantExpiresAt")),intentDigest(nonce),web);
        if(rows.size()>1)throw new SecurityException("Ambiguous CIMD intent.");return rows.stream().findFirst();
    }
    @Override public void approve(UUID id,UUID web,Set<AiScope> scopes){call("AiCimdApproveOAuthV1",r->true,id,web,AiScope.mask(scopes));}
    @Override public void complete(UUID id,UUID web,Set<AiScope> scopes,String code){call("AiCimdCompleteOAuthV1",r->true,id,web,AiScope.mask(scopes),AiOAuthCredentialStore.digest(code),UUID.randomUUID());}
    @Override public void deny(UUID id,UUID web){call("AiCimdDenyOAuthV1",r->true,id,web);}
    public Optional<AiOAuthExchangeStore.Owner> resolve(String credential,String client,String kind) {
        contract.requireWireNamespace(client);
        String proc=switch(kind){case "CODE"->"AiCimdResolveCodeV1";case "REFRESH"->"AiCimdResolveRefreshV1";case "REVOKE"->"AiCimdResolveRevocationV1";default->throw new IllegalArgumentException("Unsupported credential kind.");};
        var rows=call(proc,r->new AiOAuthExchangeStore.Owner(r.getInt("ShaleClientId"),r.getInt("UserId")),digest(credential,kind),client,issuer,resource);
        if(rows.size()>1)throw new SecurityException("Ambiguous CIMD owner.");return rows.stream().findFirst();
    }
    public ClientProof clientProof(String credential,String client,String kind) {
        contract.requireWireNamespace(client);
        return AiSql.one(call("AiCimdLoadClientProofV1",r->{
            String method=r.getString("AuthMethod");
            if("none".equals(method))return new ClientProof(method,null,null,null,null,null);
            try {
                var rsa=(RSAPublicKey)KeyFactory.getInstance("RSA").generatePublic(new RSAPublicKeySpec(new BigInteger(1,r.getBytes("Modulus")),BigInteger.valueOf(r.getInt("Exponent"))));
                return new ClientProof(method,AiSql.uuid(r,"CredentialId"),r.getString("Kid"),rsa,AiSql.instant(r,"ValidFrom"),AiSql.instant(r,"KeyExpiresAt"));
            }catch(java.security.GeneralSecurityException failure){throw new SQLException("CIMD key unavailable.");}
        },digest(credential,kind),client,issuer,resource,kind));
    }
    /** All verification, code consume, JTI reservation, family receipt, tokens and audit are one SQL transaction. */
    public AiOAuthCredentialStore.Tokens redeem(String code,String client,String redirect,String verifier,UUID key,String jti,Instant issued,Instant expires) {
        contract.requireWireNamespace(client);
        String access=AiOAuthCredentialStore.opaque(),refresh=AiOAuthCredentialStore.opaque();
        return AiSql.one(call("AiCimdRedeemV1",r->tokens(r,access,refresh),AiOAuthCredentialStore.digest(code),client,issuer,resource,redirect,AiOAuthExchangeStore.challenge(verifier),
                AiSql.nullable(key,Types.VARCHAR),jti(key,jti,issued,expires),AiSql.nullable(issued,Types.TIMESTAMP),AiSql.nullable(expires,Types.TIMESTAMP),UUID.randomUUID(),AiOAuthCredentialStore.digest(access),AiOAuthCredentialStore.digest(refresh)));
    }
    public AiOAuthCredentialStore.Tokens refresh(String token,String client,UUID key,String jti,Instant issued,Instant expires) {
        contract.requireWireNamespace(client);
        String access=AiOAuthCredentialStore.opaque(),refresh=AiOAuthCredentialStore.opaque();
        var result=AiSql.one(call("AiCimdRefreshV1",r->r.getBoolean("Denied")?new AiOAuthCredentialStore.Tokens("","",Instant.EPOCH,Instant.EPOCH,Set.of()):tokens(r,access,refresh),
                AiOAuthCredentialStore.digest(token),client,issuer,resource,AiSql.nullable(key,Types.VARCHAR),jti(key,jti,issued,expires),AiSql.nullable(issued,Types.TIMESTAMP),AiSql.nullable(expires,Types.TIMESTAMP),AiOAuthCredentialStore.digest(access),AiOAuthCredentialStore.digest(refresh),UUID.randomUUID()));
        if(result.scopes().isEmpty())throw new SecurityException("CIMD refresh unavailable.");return result;
    }
    public void revoke(String token,String client,byte[] digest,String method,String kid,String jti,Instant issued,Instant expires) {
        contract.requireWireNamespace(client);
        call("AiCimdRevokeCredentialV1",r->true,AiOAuthCredentialStore.revocationDigest(token),client,issuer,resource,digest,method,AiSql.nullable(kid,Types.NVARCHAR),revocationJti(method,kid,jti,issued,expires),AiSql.nullable(issued,Types.TIMESTAMP),AiSql.nullable(expires,Types.TIMESTAMP),UUID.randomUUID());
    }
    public void publishMetadata(UUID application,long revision,byte[] digest,String redirects,String keys,Instant freshUntil) {
        call("AiCimdPublishMetadataV1",r->true,application,revision,digest,redirects,keys,freshUntil);
    }
    public record Publication(UUID application,long revision) {}
    public Publication resolvePublication(String external,byte[] digest,int methods) {
        contract.requireWireNamespace(external);
        return AiSql.one(call("AiCimdResolvePublicationV1",r->new Publication(AiSql.uuid(r,"ApplicationId"),r.getLong("Revision")),external,issuer,resource,digest,methods));
    }
    public void revokeUnknown(String external,byte[] digest,String method,String kid,String jti,Instant issued,Instant expires) {
        contract.requireWireNamespace(external);
        call("AiCimdRevokeUnknownV1",r->true,external,issuer,resource,digest,method,AiSql.nullable(kid,Types.NVARCHAR),revocationJti(method,kid,jti,issued,expires),AiSql.nullable(issued,Types.TIMESTAMP),AiSql.nullable(expires,Types.TIMESTAMP));
    }
    private static byte[] revocationJti(String method,String kid,String jti,Instant issued,Instant expires) {
        if("none".equals(method)) {
            if(kid!=null||jti!=null||issued!=null||expires!=null)throw new SecurityException("Unexpected client assertion.");return null;
        }
        if(!"private_key_jwt".equals(method)||kid==null||kid.isEmpty()||kid.length()>128||jti==null||jti.isBlank()||jti.length()>256||issued==null||expires==null)throw new SecurityException("Client assertion required.");
        return AiSql.hash(jti.getBytes(StandardCharsets.UTF_8));
    }
    private static AiOAuthCredentialStore.Tokens tokens(ResultSet r,String access,String refresh)throws SQLException{return new AiOAuthCredentialStore.Tokens(access,refresh,AiSql.instant(r,"IssuedAt"),AiSql.instant(r,"AccessExpiresAt"),AiScope.fromMask(r.getInt("ScopeMask")));}
    private static byte[] intentDigest(String nonce) {
        if(nonce==null||!nonce.matches("[A-Za-z0-9_-]{43,128}={0,2}"))throw new IllegalArgumentException("Bounded consent nonce required.");
        return AiSql.hash(nonce.getBytes(StandardCharsets.US_ASCII));
    }
    private static byte[] digest(String credential,String kind){return "REVOKE".equals(kind)?AiOAuthCredentialStore.revocationDigest(credential):AiOAuthCredentialStore.digest(credential);}
    private static byte[] jti(UUID key,String jti,Instant issued,Instant expires) {
        if(key==null){if(jti!=null||issued!=null||expires!=null)throw new SecurityException("Unexpected assertion.");return null;}
        if(jti==null||jti.isBlank()||jti.length()>256||issued==null||expires==null)throw new SecurityException("Client proof unavailable.");
        return AiSql.hash(jti.getBytes(StandardCharsets.UTF_8));
    }
}
