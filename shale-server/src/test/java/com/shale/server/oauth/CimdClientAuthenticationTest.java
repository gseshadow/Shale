package com.shale.server.oauth;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.*;
import com.shale.data.security.ai.AiCimdOAuthStore.ClientProof;
import java.security.*;
import java.security.interfaces.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class CimdClientAuthenticationTest {
    static final CimdClientIdentifier CLIENT=new CimdClientIdentifier("https://client.test/Exact%2Fidentity.json");
    static final Instant NOW=Instant.parse("2026-11-10T22:00:00Z");
    static final Clock CLOCK=Clock.fixed(NOW,ZoneOffset.UTC);
    static final String ENDPOINT="https://shale.test/oauth/mcp/token";
    static KeyPair pair;static ClientProof selected;
    @BeforeAll static void keys()throws Exception {
        var generator=KeyPairGenerator.getInstance("RSA");generator.initialize(2048);pair=generator.generateKeyPair();
        selected=new ClientProof("private_key_jwt",UUID.randomUUID(),"remote-key-2026",(RSAPublicKey)pair.getPublic(),NOW.minusSeconds(60),NOW.plusSeconds(3600));
    }
    String signed(String issuer,String subject,String endpoint,String kid,Instant issued,Instant expires)throws Exception {
        var jwt=new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(kid).build(),new JWTClaimsSet.Builder().issuer(issuer).subject(subject).audience(endpoint).issueTime(Date.from(issued)).expirationTime(Date.from(expires)).jwtID("bounded-test-jti").build());
        jwt.sign(new RSASSASigner((RSAPrivateKey)pair.getPrivate()));return jwt.serialize();
    }
    String valid()throws Exception{return signed(CLIENT.value(),CLIENT.value(),ENDPOINT,selected.kid(),NOW,NOW.plusSeconds(60));}
    @Test void externalClaimsAndRemoteKidMapToExactApprovedLocalKey()throws Exception {
        var proof=CimdClientAuthentication.verify(CLIENT,selected,"private_key_jwt",valid(),ENDPOINT,CLOCK);
        assertEquals(selected.keyId(),proof.keyId());assertEquals("CimdAssertion[redacted]",proof.toString());
    }
    @Test void clientMethodNeverDowngradesOrAcceptsUnexpectedAssertion()throws Exception {
        assertThrows(SecurityException.class,()->CimdClientAuthentication.verify(CLIENT,selected,"none",null,ENDPOINT,CLOCK));
        var publicClient=new ClientProof("none",null,null,null,null,null);
        assertNull(CimdClientAuthentication.verify(CLIENT,publicClient,"none",null,ENDPOINT,CLOCK));
        assertThrows(SecurityException.class,()->CimdClientAuthentication.verify(CLIENT,publicClient,"none",valid(),ENDPOINT,CLOCK));
        assertThrows(SecurityException.class,()->CimdClientAuthentication.verify(CLIENT,selected,"private_key_jwt",null,ENDPOINT,CLOCK));
    }
    @Test void internalIdentityNormalizedIdentityWrongKeyAndAudienceAreRejected()throws Exception {
        for(String identity:List.of("internal-id",CLIENT.value().replace("%2F","%2f"),CLIENT.value().toUpperCase(Locale.ROOT))) {
            String jwt=signed(identity,identity,ENDPOINT,selected.kid(),NOW,NOW.plusSeconds(60));
            assertThrows(SecurityException.class,()->CimdClientAuthentication.verify(CLIENT,selected,"private_key_jwt",jwt,ENDPOINT,CLOCK));
        }
        for(String jwt:List.of(signed(CLIENT.value(),CLIENT.value(),ENDPOINT+"/",selected.kid(),NOW,NOW.plusSeconds(60)),signed(CLIENT.value(),CLIENT.value(),ENDPOINT,"other-kid",NOW,NOW.plusSeconds(60)),signed(CLIENT.value(),"other",ENDPOINT,selected.kid(),NOW,NOW.plusSeconds(60))))
            assertThrows(SecurityException.class,()->CimdClientAuthentication.verify(CLIENT,selected,"private_key_jwt",jwt,ENDPOINT,CLOCK));
    }
    @Test void assertionAndKeyExpiryRemainBounded()throws Exception {
        for(String jwt:List.of(signed(CLIENT.value(),CLIENT.value(),ENDPOINT,selected.kid(),NOW.minusSeconds(61),NOW),signed(CLIENT.value(),CLIENT.value(),ENDPOINT,selected.kid(),NOW,NOW.plusSeconds(61)),signed(CLIENT.value(),CLIENT.value(),ENDPOINT,selected.kid(),NOW.plusSeconds(1),NOW.plusSeconds(60))))
            assertThrows(SecurityException.class,()->CimdClientAuthentication.verify(CLIENT,selected,"private_key_jwt",jwt,ENDPOINT,CLOCK));
        var expired=new ClientProof("private_key_jwt",selected.keyId(),selected.kid(),selected.rsa(),NOW.minusSeconds(60),NOW);
        assertThrows(SecurityException.class,()->CimdClientAuthentication.verify(CLIENT,expired,"private_key_jwt",valid(),ENDPOINT,CLOCK));
    }
}
