package com.shale.server.oauth;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.*;
import com.nimbusds.jwt.*;
import com.shale.data.security.ai.*;
import com.shale.core.security.ai.AiScope;
import org.junit.jupiter.api.*;
import java.net.URI;
import java.security.*;
import java.security.interfaces.*;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PinnedClientAssertionTest {
    static KeyPair pair,other,weak; static final UUID KEY=UUID.randomUUID();
    static final Instant NOW=Instant.parse("2026-10-22T16:00:00Z");
    static final String ENDPOINT="https://shale.test/oauth/mcp/token";
    @BeforeAll static void keys()throws Exception {var g=KeyPairGenerator.getInstance("RSA");g.initialize(2048);pair=g.generateKeyPair();other=g.generateKeyPair();g.initialize(1024);weak=g.generateKeyPair();}
    AiOAuthTransactionStore.Client client(List<AiOAuthTransactionStore.Key> keys) {
        return new AiOAuthTransactionStore.Client(7,UUID.randomUUID(),"client","Synthetic","CONFIDENTIAL",AiScope.fromMask(1),NOW.plusSeconds(1000),new AiIntegrationSecurityService.RowVersion(new byte[8]),"https://shale.test/oauth/mcp","https://shale.test/mcp",Set.of("https://client.test/callback"),keys);
    }
    AiOAuthTransactionStore.Key key(KeyPair p,Instant from,Instant exp) {return new AiOAuthTransactionStore.Key(KEY,(RSAPublicKey)p.getPublic(),"RS256",from,exp);}
    AiOAuthTransactionStore.Client client() {return client(List.of(key(pair,NOW.minusSeconds(5),NOW.plusSeconds(100))));}
    JWTClaimsSet.Builder claims() {return new JWTClaimsSet.Builder().issuer("client").subject("client").audience(ENDPOINT).issueTime(Date.from(NOW)).expirationTime(Date.from(NOW.plusSeconds(60))).jwtID("bounded-synthetic-jti");}
    String sign(JWSHeader h,JWTClaimsSet.Builder c,KeyPair p)throws Exception {var jwt=new SignedJWT(h,c.build());jwt.sign(new RSASSASigner(p.getPrivate()));return jwt.serialize();}
    JWSHeader header() {return new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(KEY.toString()).build();}
    PinnedClientAssertion.Proof verify(String jwt,AiOAuthTransactionStore.Client c) {return PinnedClientAssertion.verify(jwt,c,ENDPOINT,Clock.fixed(NOW,ZoneOffset.UTC));}
    @Test void validPinnedRs256HasExactClientKeyAudienceAndSixtySecondLifetime()throws Exception {
        var p=verify(sign(header(),claims(),pair),client());assertEquals(KEY,p.keyId());assertEquals(NOW.plusSeconds(60),p.expiresAt());assertEquals("ClientProof[redacted]",p.toString());
    }
    @Test void wrongClaimsAndMissingOrExcessiveTimesFailClosed()throws Exception {
        var bad=List.of(claims().issuer("wrong"),claims().subject("wrong"),claims().audience(ENDPOINT+"/"),claims().audience(List.of(ENDPOINT,"https://evil.test")),claims().expirationTime(Date.from(NOW)),claims().expirationTime(Date.from(NOW.plusSeconds(61))),claims().issueTime(Date.from(NOW.plusSeconds(1))),claims().issueTime(null),claims().expirationTime(null),claims().jwtID(null),claims().jwtID(" "),claims().jwtID("x".repeat(257)),claims().notBeforeTime(Date.from(NOW.plusSeconds(1))));
        for(var c:bad) {String jwt=sign(header(),c,pair);assertThrows(SecurityException.class,()->verify(jwt,client()));}
    }
    @Test void wrongRevokedExpiredFutureWeakAndUnpinnedKeysFailClosed()throws Exception {
        String good=sign(header(),claims(),pair),wrong=sign(header(),claims(),other);
        assertThrows(SecurityException.class,()->verify(wrong,client()));
        assertThrows(SecurityException.class,()->verify(good,client(List.of()))); // revoked keys never appear in live SQL snapshot
        for(var key:List.of(key(pair,NOW.minusSeconds(100),NOW),key(pair,NOW.plusSeconds(1),NOW.plusSeconds(100)),key(weak,NOW.minusSeconds(1),NOW.plusSeconds(100))))
            assertThrows(SecurityException.class,()->verify(good,client(List.of(key))));
        String unknown=sign(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(UUID.randomUUID().toString()).build(),claims(),pair);
        assertThrows(SecurityException.class,()->verify(unknown,client()));
    }
    @Test void callerUrlsEmbeddedKeysUnsupportedAlgorithmsAndMalformedAssertionsNeverFetch()throws Exception {
        for(var h:List.of(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(KEY.toString()).jwkURL(URI.create("https://attacker.invalid/jwks")).build(),new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(KEY.toString()).x509CertURL(URI.create("https://attacker.invalid/cert")).build(),new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(KEY.toString()).jwk(new com.nimbusds.jose.jwk.RSAKey.Builder((RSAPublicKey)other.getPublic()).build()).build(),new JWSHeader.Builder(JWSAlgorithm.PS256).keyID(KEY.toString()).build())) {
            String jwt=sign(h,claims(),pair);assertThrows(SecurityException.class,()->verify(jwt,client()));
        }
        for(var alg:List.of(JWSAlgorithm.HS256,JWSAlgorithm.ES256)) {
            var j=new SignedJWT(new JWSHeader.Builder(alg).keyID(KEY.toString()).build(),claims().build());
            if(alg.equals(JWSAlgorithm.HS256))j.sign(new MACSigner(new byte[32]));
            else {var g=KeyPairGenerator.getInstance("EC");g.initialize(256);j.sign(new ECDSASigner((ECPrivateKey)g.generateKeyPair().getPrivate()));}
            assertThrows(SecurityException.class,()->verify(j.serialize(),client()));
        }
        for(String jwt:List.of("malformed","x".repeat(8193)))assertThrows(SecurityException.class,()->verify(jwt,client()));
    }
}
