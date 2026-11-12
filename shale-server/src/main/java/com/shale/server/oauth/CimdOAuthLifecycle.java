package com.shale.server.oauth;

import com.shale.data.security.ai.*;
import com.shale.server.oauth.cimd.CimdMetadataService;
import java.time.*;
import java.util.*;
import java.util.function.Function;

/** PR A lifecycle composed by the explicit default-OFF CIMD connection. No legacy identity fallback.
 * Metadata is retrieved through PR 2 and must match the governed SQL publication exactly. */
public final class CimdOAuthLifecycle {
    private final CimdMetadataService metadata;
    private final AiCimdOAuthStore publisher,resolver,neutral;
    private final Function<AiOAuthExchangeStore.Owner,AiCimdOAuthStore> issuers;
    private final McpOAuthProperties properties;
    private final Clock clock;
    private final AiDcrApplicationStore storedDcr;
    public CimdOAuthLifecycle(CimdMetadataService metadata,AiCimdOAuthStore publisher,AiCimdOAuthStore resolver,AiCimdOAuthStore neutral,
            Function<AiOAuthExchangeStore.Owner,AiCimdOAuthStore> issuers,McpOAuthProperties properties,Clock clock) {
        this(metadata,publisher,resolver,neutral,issuers,properties,clock,null);
    }
    /** Explicit dormant V2 composition; C1 creates no Spring bean or HTTP registration route. */
    public CimdOAuthLifecycle(CimdMetadataService metadata,AiCimdOAuthStore publisher,AiCimdOAuthStore resolver,AiCimdOAuthStore neutral,
            Function<AiOAuthExchangeStore.Owner,AiCimdOAuthStore> issuers,McpOAuthProperties properties,Clock clock,AiDcrApplicationStore storedDcr) {
        this.storedDcr=storedDcr;
        if(storedDcr!=null&&(publisher.contract()!=AiExternalContract.EXTERNAL_V2||resolver.contract()!=AiExternalContract.EXTERNAL_V2||neutral.contract()!=AiExternalContract.EXTERNAL_V2))throw denied();
        this.metadata=Objects.requireNonNull(metadata);this.publisher=Objects.requireNonNull(publisher);this.resolver=Objects.requireNonNull(resolver);
        this.neutral=Objects.requireNonNull(neutral);this.issuers=Objects.requireNonNull(issuers);this.properties=properties;this.clock=clock;
    }
    /** Does not advance a registry revision or restore a disabled application. Changed bytes require approval again. */
    public CimdMetadataService.Snapshot prepare(CimdClientIdentifier client) {
        var snapshot=metadata.retrieve(client);
        if(!properties.issuer().equals(snapshot.issuer())||!client.equals(snapshot.identifier())||!snapshot.expiresAt().isAfter(clock.instant()))throw denied();
        int methods=(snapshot.methods().contains("none")?1:0)|(snapshot.methods().contains("private_key_jwt")?2:0);
        byte[] digest=HexFormat.of().parseHex(snapshot.revision());
        var publication=publisher.resolvePublication(client.value(),digest,methods);
        var keys=new ArrayList<Map<String,Object>>();
        snapshot.keys().stream().sorted(Comparator.comparing(CimdMetadataService.SigningKey::kid)).forEach(k->{
            if(!k.rsa().getPublicExponent().equals(java.math.BigInteger.valueOf(65537)))throw denied();
            byte[] modulus=k.rsa().getModulus().toByteArray();if(modulus[0]==0)modulus=Arrays.copyOfRange(modulus,1,modulus.length);
            var published=new TreeMap<String,Object>();published.put("kid",k.kid());published.put("modulus",HexFormat.of().formatHex(modulus));published.put("exponent",65537);keys.add(published);
        });
        var json=new tools.jackson.databind.ObjectMapper();
        publisher.publishMetadata(publication.application(),publication.revision(),digest,json.writeValueAsString(snapshot.redirects()),json.writeValueAsString(keys),snapshot.expiresAt());
        return snapshot;
    }
    /** Inspect CIMD through the unchanged secure fetcher, DCR through stored immutable metadata only. */
    OAuthApplicationMetadata inspect(OAuthApplicationIdentifier client) {
        if(client.source()==OAuthApplicationIdentifier.Source.CIMD)return OAuthApplicationMetadata.cimd(metadata.retrieve(client.cimd()));
        if(storedDcr==null)throw denied();
        var value=storedDcr.prepare(new DcrClientIdentifier(client.value()));
        if(!client.value().equals(value.identifier().value())||!value.freshUntil().isAfter(clock.instant())||value.methods()<1||value.methods()>2)throw denied();
        boolean confidential=value.methods()==2;
        var json=new tools.jackson.databind.ObjectMapper();
        var redirectJson=json.readTree(value.redirects());var keyJson=json.readTree(value.keys());
        if(!redirectJson.isArray()||redirectJson.isEmpty()||redirectJson.size()>5||!keyJson.isArray()||keyJson.size()>2)throw denied();
        var redirects=new ArrayList<String>();var keys=new ArrayList<CimdMetadataService.SigningKey>();var kids=new HashSet<String>();
        for(var item:redirectJson) {
            if(!item.isString())throw denied();var uri=item.asString();
            DcrApplicationRegistration.redirect(uri,confidential);if(redirects.contains(uri))throw denied();redirects.add(uri);
        }
        try {
            for(var item:keyJson) {
                if(!item.path("kid").isString()||!item.path("modulus").isString())throw denied();
                String kid=item.path("kid").asString(),modulus=item.path("modulus").asString();
                if(kid==null||kid.isBlank()||kid.length()>128||!kids.add(kid)||modulus==null||!modulus.matches("[0-9a-f]{512,1024}")||item.path("exponent").asInt()!=65537)throw denied();
                var rsa=(java.security.interfaces.RSAPublicKey)java.security.KeyFactory.getInstance("RSA").generatePublic(new java.security.spec.RSAPublicKeySpec(new java.math.BigInteger(1,HexFormat.of().parseHex(modulus)),java.math.BigInteger.valueOf(65537)));
                if(rsa.getModulus().bitLength()<2048||rsa.getModulus().bitLength()>4096)throw denied();
                keys.add(new CimdMetadataService.SigningKey(kid,rsa));
            }
        }catch(java.security.GeneralSecurityException invalid){throw denied();}
        if(confidential==keys.isEmpty())throw denied();
        return new OAuthApplicationMetadata(properties.issuer(),client,value.name(),redirects,Set.of(confidential?"private_key_jwt":"none"),
                Set.of("authorization_code","refresh_token"),keys,HexFormat.of().formatHex(value.digest()),value.freshUntil(),com.shale.core.security.ai.AiScope.fromMask(value.scopes()));
    }
    public OAuthApplicationMetadata prepare(OAuthApplicationIdentifier client) {
        return client.source()==OAuthApplicationIdentifier.Source.CIMD?OAuthApplicationMetadata.cimd(prepare(client.cimd())):inspect(client);
    }
    /** Reuse the maintained browser/consent adapter and original Shale WEB authentication. */
    public AiOAuthBrowserStore browser(AiCimdOAuthStore store) {
        if(storedDcr!=null&&store.contract()!=AiExternalContract.EXTERNAL_V2)throw denied();
        var delegate=store.browser(()->ShaleOAuthHuman.current().session());
        return new AiOAuthBrowserStore(){
            @Override public boolean externalIdentity(){return true;}
            @Override public Optional<AiOAuthTransactionStore.Client> loadClient(String client){
                var publication=prepare(new OAuthApplicationIdentifier(client));
                return delegate.loadClient(client).map(c->new AiOAuthTransactionStore.Client(c.tenant(),c.integration(),c.clientId(),publication.name(),
                        c.type(),c.scopes(),c.expiresAt(),c.version(),c.issuer(),c.resource(),c.redirects(),c.keys()));
            }
            @Override public void begin(UUID id,UUID web,String client,String redirect,String challenge,String state,Set<com.shale.core.security.ai.AiScope> scopes,String nonce){prepare(new OAuthApplicationIdentifier(client));delegate.begin(id,web,client,redirect,challenge,state,scopes,nonce);}
            @Override public Optional<AiOAuthTransactionStore.Intent> findIntent(String nonce,UUID web){var found=delegate.findIntent(nonce,web);found.ifPresent(intent->prepare(new OAuthApplicationIdentifier(intent.clientId())));return found;}
            @Override public void approve(UUID id,UUID web,Set<com.shale.core.security.ai.AiScope> scopes){delegate.approve(id,web,scopes);}
            @Override public void complete(UUID id,UUID web,Set<com.shale.core.security.ai.AiScope> scopes,String code){delegate.complete(id,web,scopes,code);}
            @Override public void deny(UUID id,UUID web){delegate.deny(id,web);}
        };
    }
    record Exchange(AiCimdOAuthStore store,String client,String method,CimdClientAuthentication.Assertion proof,
                    OAuthApplicationMetadata snapshot) {
        @Override public String toString(){return "CimdExchange[redacted]";}
    }
    Exchange authenticateExchange(String client,String credential,String kind,String method,String assertion) {
        var id=new OAuthApplicationIdentifier(client);var snapshot=prepare(id);
        var store=issuers.apply(resolver.resolve(credential,client,kind).orElseThrow(CimdOAuthLifecycle::denied));
        if(id.source()==OAuthApplicationIdentifier.Source.DCR&&store.contract()!=AiExternalContract.EXTERNAL_V2)throw denied();
        var selected=store.clientProof(credential,client,kind);
        var proof=CimdClientAuthentication.verify(id.value(),selected,method,assertion,properties.issuer()+"/token",clock);
        return new Exchange(store,client,selected.method(),proof,snapshot);
    }
    AiOAuthCredentialStore.Tokens redeem(Exchange exchange,String code,String redirect,String verifier) {
        var proof=exchange.proof();
        return exchange.store().redeem(code,exchange.client(),redirect,verifier,proof==null?null:proof.keyId(),proof==null?null:proof.jti(),proof==null?null:proof.issuedAt(),proof==null?null:proof.expiresAt());
    }
    AiOAuthCredentialStore.Tokens refresh(Exchange exchange,String refresh) {
        var proof=exchange.proof();
        return exchange.store().refresh(refresh,exchange.client(),proof==null?null:proof.keyId(),proof==null?null:proof.jti(),proof==null?null:proof.issuedAt(),proof==null?null:proof.expiresAt());
    }
    public AiOAuthCredentialStore.Tokens redeem(String client,String code,String redirect,String verifier,String method,String assertion) {
        return redeem(authenticateExchange(client,code,"CODE",method,assertion),code,redirect,verifier);
    }
    public AiOAuthCredentialStore.Tokens refresh(String client,String refresh,String method,String assertion) {
        return refresh(authenticateExchange(client,refresh,"REFRESH",method,assertion),refresh);
    }
    record Revocation(String client,byte[] digest,String method,String kid,CimdClientAuthentication.Assertion proof,OAuthApplicationMetadata snapshot) {
        @Override public String toString(){return "CimdRevocation[redacted]";}
    }
    Revocation authenticateRevocation(String client,String method,String assertion) {
        var id=new OAuthApplicationIdentifier(client);var snapshot=prepare(id);
        if(!snapshot.methods().contains(method))throw denied();
        String kid=null;AiCimdOAuthStore.ClientProof selected;
        if("none".equals(method))selected=new AiCimdOAuthStore.ClientProof("none",null,null,null,null,null);
        else if("private_key_jwt".equals(method)) {
            if(assertion==null||assertion.length()>8192)throw denied();
            try{kid=com.nimbusds.jwt.SignedJWT.parse(assertion).getHeader().getKeyID();}catch(Exception invalid){throw denied();}
            String selectedKid=kid;
            var key=snapshot.keys().stream().filter(k->k.kid().equals(selectedKid)).findFirst().orElseThrow(CimdOAuthLifecycle::denied);
            selected=new AiCimdOAuthStore.ClientProof(method,null,kid,key.rsa(),Instant.EPOCH,snapshot.expiresAt());
        } else throw denied();
        var proof=CimdClientAuthentication.verify(id.value(),selected,method,assertion,properties.issuer()+"/revoke",clock);
        return new Revocation(client,HexFormat.of().parseHex(snapshot.revision()),method,kid,proof,snapshot);
    }
    void revoke(Revocation revocation,String credential) {
        var proof=revocation.proof();var owner=resolver.resolve(credential,revocation.client(),"REVOKE");
        if(owner.isPresent())issuers.apply(owner.get()).revoke(credential,revocation.client(),revocation.digest(),revocation.method(),revocation.kid(),proof==null?null:proof.jti(),proof==null?null:proof.issuedAt(),proof==null?null:proof.expiresAt());
        else neutral.revokeUnknown(revocation.client(),revocation.digest(),revocation.method(),revocation.kid(),proof==null?null:proof.jti(),proof==null?null:proof.issuedAt(),proof==null?null:proof.expiresAt());
    }
    /** Known credentials remain owner-bound; unknown credentials never select a tenant. */
    public void revoke(String client,String credential,String method,String assertion) {
        revoke(authenticateRevocation(client,method,assertion),credential);
    }
    private static SecurityException denied(){return new SecurityException("CIMD authorization unavailable.");}
}
