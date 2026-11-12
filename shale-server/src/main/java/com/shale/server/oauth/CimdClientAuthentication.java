package com.shale.server.oauth;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.SignedJWT;
import com.shale.data.security.ai.AiCimdOAuthStore.ClientProof;
import java.time.*;
import java.util.*;

/** Pure signature/profile verification against the SQL-bound publication key. No URL/key fallback.
 * The returned proof must be reserved atomically by the versioned SQL exchange/rotation/revocation. */
final class CimdClientAuthentication {
    record Assertion(UUID keyId,String jti,Instant issuedAt,Instant expiresAt) {
        @Override public String toString(){return "CimdAssertion[redacted]";}
    }
    static Assertion verify(CimdClientIdentifier client,ClientProof selected,String method,String assertion,String endpoint,Clock clock) {
        return verify(client.value(),selected,method,assertion,endpoint,clock);
    }
    static Assertion verify(String exactClient,ClientProof selected,String method,String assertion,String endpoint,Clock clock) {
        new OAuthApplicationIdentifier(exactClient);
        if("none".equals(selected.method())) {
            if(!"none".equals(method)||assertion!=null||selected.keyId()!=null||selected.rsa()!=null)throw denied();
            return null;
        }
        try {
            if(!"private_key_jwt".equals(selected.method())||!"private_key_jwt".equals(method)||assertion==null||assertion.length()>8192||selected.kid()==null||selected.rsa()==null)throw denied();
            var now=clock.instant();
            if(selected.validFrom()==null||selected.expiresAt()==null||selected.validFrom().isAfter(now)||!selected.expiresAt().isAfter(now)||selected.rsa().getModulus().bitLength()<2048)throw denied();
            var jwt=SignedJWT.parse(assertion);var h=jwt.getHeader();
            if(!JWSAlgorithm.RS256.equals(h.getAlgorithm())||!selected.kid().equals(h.getKeyID())||h.getJWKURL()!=null||h.getX509CertURL()!=null||h.getJWK()!=null||h.getX509CertChain()!=null||h.getCriticalParams()!=null||!h.getCustomParams().isEmpty())throw denied();
            if(!jwt.verify(new RSASSAVerifier(selected.rsa())))throw denied();
            var c=jwt.getJWTClaimsSet();
            if(!exactClient.equals(c.getIssuer())||!exactClient.equals(c.getSubject())||!List.of(endpoint).equals(c.getAudience())||c.getIssueTime()==null||c.getExpirationTime()==null||c.getJWTID()==null||c.getJWTID().isBlank()||c.getJWTID().length()>256)throw denied();
            var issued=c.getIssueTime().toInstant();var expires=c.getExpirationTime().toInstant();
            if(issued.isAfter(now)||!expires.isAfter(now)||!expires.isAfter(issued)||Duration.between(issued,expires).compareTo(Duration.ofSeconds(60))>0||(c.getNotBeforeTime()!=null&&c.getNotBeforeTime().toInstant().isAfter(now)))throw denied();
            return new Assertion(selected.keyId(),c.getJWTID(),issued,expires);
        }catch(java.text.ParseException|JOSEException|IllegalArgumentException invalid){throw denied();}
    }
    private static SecurityException denied(){return new SecurityException("CIMD client authentication unavailable.");}
}
