package com.shale.server.oauth;

import com.shale.data.security.ai.*;
import com.shale.server.oauth.cimd.CimdMetadataService;
import java.time.*;
import java.util.*;
import java.util.function.Function;

/** Isolated composition candidate: no Spring bean or production identity fallback.
 * A future runtime attestor must supply four independent confined identity factories.
 * All metadata comes through PR 2; current governed publication must match exactly. */
public final class CimdOAuthLifecycle {
    private final CimdMetadataService metadata;
    private final AiCimdOAuthStore publisher,resolver,neutral;
    private final Function<AiOAuthExchangeStore.Owner,AiCimdOAuthStore> issuers;
    private final McpOAuthProperties properties;
    private final Clock clock;
    public CimdOAuthLifecycle(CimdMetadataService metadata,AiCimdOAuthStore publisher,AiCimdOAuthStore resolver,AiCimdOAuthStore neutral,
            Function<AiOAuthExchangeStore.Owner,AiCimdOAuthStore> issuers,McpOAuthProperties properties,Clock clock) {
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
    /** Reuse the maintained browser/consent adapter and original Shale WEB authentication. */
    public AiOAuthBrowserStore browser(AiCimdOAuthStore store) {
        var delegate=store.browser(()->ShaleOAuthHuman.current().session());
        return new AiOAuthBrowserStore(){
            @Override public boolean externalIdentity(){return true;}
            @Override public Optional<AiOAuthTransactionStore.Client> loadClient(String client){prepare(new CimdClientIdentifier(client));return delegate.loadClient(client);}
            @Override public void begin(UUID id,UUID web,String client,String redirect,String challenge,String state,Set<com.shale.core.security.ai.AiScope> scopes,String nonce){prepare(new CimdClientIdentifier(client));delegate.begin(id,web,client,redirect,challenge,state,scopes,nonce);}
            @Override public Optional<AiOAuthTransactionStore.Intent> findIntent(String nonce,UUID web){var found=delegate.findIntent(nonce,web);found.ifPresent(intent->prepare(new CimdClientIdentifier(intent.clientId())));return found;}
            @Override public void approve(UUID id,UUID web,Set<com.shale.core.security.ai.AiScope> scopes){delegate.approve(id,web,scopes);}
            @Override public void complete(UUID id,UUID web,Set<com.shale.core.security.ai.AiScope> scopes,String code){delegate.complete(id,web,scopes,code);}
            @Override public void deny(UUID id,UUID web){delegate.deny(id,web);}
        };
    }
    public AiOAuthCredentialStore.Tokens redeem(String client,String code,String redirect,String verifier,String method,String assertion) {
        var id=new CimdClientIdentifier(client);prepare(id);
        var store=issuers.apply(resolver.resolve(code,client,"CODE").orElseThrow(CimdOAuthLifecycle::denied));
        var selected=store.clientProof(code,client,"CODE");
        var proof=CimdClientAuthentication.verify(id,selected,method,assertion,properties.issuer()+"/token",clock);
        return store.redeem(code,client,redirect,verifier,proof==null?null:proof.keyId(),proof==null?null:proof.jti(),proof==null?null:proof.issuedAt(),proof==null?null:proof.expiresAt());
    }
    public AiOAuthCredentialStore.Tokens refresh(String client,String refresh,String method,String assertion) {
        var id=new CimdClientIdentifier(client);prepare(id);
        var store=issuers.apply(resolver.resolve(refresh,client,"REFRESH").orElseThrow(CimdOAuthLifecycle::denied));
        var proof=CimdClientAuthentication.verify(id,store.clientProof(refresh,client,"REFRESH"),method,assertion,properties.issuer()+"/token",clock);
        return store.refresh(refresh,client,proof==null?null:proof.keyId(),proof==null?null:proof.jti(),proof==null?null:proof.issuedAt(),proof==null?null:proof.expiresAt());
    }
    /** Known credentials remain owner-bound; unknown credentials authenticate without a tenant lookup. */
    public void revoke(String client,String credential,String method,String assertion) {
        var id=new CimdClientIdentifier(client);var snapshot=prepare(id);
        var owner=resolver.resolve(credential,client,"REVOKE");
        if(owner.isPresent()) {
            var store=issuers.apply(owner.get());
            var proof=CimdClientAuthentication.verify(id,store.clientProof(credential,client,"REVOKE"),method,assertion,properties.issuer()+"/revoke",clock);
            store.revoke(credential,client,proof==null?null:proof.keyId(),proof==null?null:proof.jti(),proof==null?null:proof.issuedAt(),proof==null?null:proof.expiresAt());
        } else {
            if(!snapshot.methods().contains(method))throw denied();
            String kid=null;AiCimdOAuthStore.ClientProof selected;
            if("none".equals(method))selected=new AiCimdOAuthStore.ClientProof("none",null,null,null,null,null);
            else if("private_key_jwt".equals(method)) {
                try{kid=com.nimbusds.jwt.SignedJWT.parse(assertion).getHeader().getKeyID();}catch(Exception invalid){throw denied();}
                String selectedKid=kid;
                var key=snapshot.keys().stream().filter(k->k.kid().equals(selectedKid)).findFirst().orElseThrow(CimdOAuthLifecycle::denied);
                selected=new AiCimdOAuthStore.ClientProof(method,null,kid,key.rsa(),Instant.EPOCH,snapshot.expiresAt());
            } else throw denied();
            var proof=CimdClientAuthentication.verify(id,selected,method,assertion,properties.issuer()+"/revoke",clock);
            neutral.revokeUnknown(client,method,kid,proof==null?null:proof.jti(),proof==null?null:proof.issuedAt(),proof==null?null:proof.expiresAt());
        }
    }
    private static SecurityException denied(){return new SecurityException("CIMD authorization unavailable.");}
}
