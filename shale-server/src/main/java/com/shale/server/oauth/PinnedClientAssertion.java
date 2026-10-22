package com.shale.server.oauth;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.SignedJWT;
import com.shale.data.security.ai.AiOAuthTransactionStore.Client;
import java.time.*;
import java.util.*;

/** Verifies only pinned local RSA material. This class has no URL loader or remote JWKS resolver. */
final class PinnedClientAssertion {
    record Proof(UUID keyId,String jti,Instant issuedAt,Instant expiresAt) {
        @Override public String toString() { return "ClientProof[redacted]"; }
    }
    static Proof verify(String assertion,Client client,String tokenEndpoint,Clock clock) {
        try {
            if(assertion==null || assertion.length()>8192) throw denied();
            var jwt=SignedJWT.parse(assertion); var h=jwt.getHeader();
            if(!JWSAlgorithm.RS256.equals(h.getAlgorithm()) || h.getJWKURL()!=null || h.getX509CertURL()!=null || h.getJWK()!=null ||
                    h.getX509CertChain()!=null || h.getCriticalParams()!=null || h.getKeyID()==null || !h.getCustomParams().isEmpty()) throw denied();
            UUID keyId=UUID.fromString(h.getKeyID());
            var now=clock.instant();
            var key=client.keys().stream().filter(k->k.id().equals(keyId)).findFirst().orElseThrow(PinnedClientAssertion::denied);
            if(!"RS256".equals(key.algorithm()) || key.rsa().getModulus().bitLength()<2048 || key.validFrom().isAfter(now) || !key.expiresAt().isAfter(now)) throw denied();
            if(!jwt.verify(new RSASSAVerifier(key.rsa()))) throw denied();
            var c=jwt.getJWTClaimsSet();
            if(!client.clientId().equals(c.getIssuer()) || !client.clientId().equals(c.getSubject()) ||
                    !List.of(tokenEndpoint).equals(c.getAudience()) || c.getIssueTime()==null || c.getExpirationTime()==null ||
                    c.getJWTID()==null || c.getJWTID().isBlank() || c.getJWTID().length()>256) throw denied();
            var iat=c.getIssueTime().toInstant(); var exp=c.getExpirationTime().toInstant();
            if(iat.isAfter(now) || !exp.isAfter(now) || !exp.isAfter(iat) || Duration.between(iat,exp).compareTo(Duration.ofSeconds(60))>0 ||
                    (c.getNotBeforeTime()!=null && c.getNotBeforeTime().toInstant().isAfter(now))) throw denied();
            return new Proof(keyId,c.getJWTID(),iat,exp);
        } catch(java.text.ParseException | JOSEException | IllegalArgumentException invalid) { throw denied(); }
    }
    private static SecurityException denied() { return new SecurityException("Client assertion unavailable."); }
}
