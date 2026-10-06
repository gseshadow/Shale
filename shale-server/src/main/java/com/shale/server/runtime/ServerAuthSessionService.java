package com.shale.server.runtime;

import java.time.Instant;
import java.util.UUID;
import com.shale.core.dto.UserSessionView;
import com.shale.core.model.ClientType;

/** Centralizes bound JWT issuance, legacy upgrade, conditional rotation, and durable logout. */
public final class ServerAuthSessionService {
    private static final java.time.Duration REMEMBER_LIFETIME=java.time.Duration.ofDays(30);
    private final ShaleAuthTokenService tokens; private final DurableSessionStore sessions;
    private final DurableSessionTokenValidator validator; private final LegacyTokenCompatibilityPolicy legacy;
    private final TokenRevocationStore legacyRevocations;
    public ServerAuthSessionService(ShaleAuthTokenService tokens,DurableSessionStore sessions,
            DurableSessionTokenValidator validator,LegacyTokenCompatibilityPolicy legacy,TokenRevocationStore legacyRevocations){
        this.tokens=tokens;this.sessions=sessions;this.validator=validator;this.legacy=legacy;this.legacyRevocations=legacyRevocations;
    }
    public String issue(ServerPrincipal principal){
        var prepared=tokens.prepare(principal);var session=sessions.create(principal,ClientType.WEB,null,prepared.tokenId(),Instant.ofEpochSecond(prepared.expiresAtEpochSeconds()));
        return tokens.issueBound(prepared,session.sessionId());
    }
    /** Issues the shared bound-token shape for a credential-verified desktop principal. */
    public IssuedSession issueDesktop(ServerPrincipal principal,Long applicationInstanceId){
        var prepared=tokens.prepare(principal);
        var session=sessions.create(principal,ClientType.DESKTOP,applicationInstanceId,prepared.tokenId(),Instant.ofEpochSecond(prepared.expiresAtEpochSeconds()));
        String token=tokens.issueBound(prepared,session.sessionId());
        return new IssuedSession(token,session.sessionId(),prepared.tokenId(),Instant.ofEpochSecond(prepared.expiresAtEpochSeconds()));
    }
    public RememberedSession issueRememberedDesktop(ServerPrincipal principal,Long applicationInstanceId,UUID installationId,RememberCredentialStore remembers){
        var prepared=tokens.prepare(principal);Instant deadline=Instant.now().plus(REMEMBER_LIFETIME);
        var session=sessions.create(principal,ClientType.DESKTOP,applicationInstanceId,prepared.tokenId(),deadline);
        String remember=RememberedSession.newSecret();
        try{remembers.create(principal,session.sessionId(),installationId,RememberedSession.hash(remember),deadline);}catch(RuntimeException failure){sessions.revoke(principal,session.sessionId(),"SECURITY");throw failure;}
        return new RememberedSession(tokens.issueBound(prepared,session.sessionId()),session.sessionId(),prepared.tokenId(),Instant.ofEpochSecond(prepared.expiresAtEpochSeconds()),remember,deadline,principal,false,false);
    }
    public java.util.Optional<RememberedSession> restoreDesktop(String presented,String replacement,UUID installationId,RememberCredentialStore remembers){
        if(!RememberedSession.valid(presented)||!RememberedSession.valid(replacement))return java.util.Optional.empty();
        Instant now=Instant.now();
        /* The principal is derived by the store, so prepare after lookup is represented by an untrusted-free two-step token id. */
        UUID replacementJti=UUID.randomUUID();return remembers.rotate(RememberedSession.hash(presented),installationId,RememberedSession.hash(replacement),replacementJti,now).flatMap(record->{
            var prepared=tokens.prepare(record.principal(),replacementJti);
            return java.util.Optional.of(new RememberedSession(tokens.issueBound(prepared,record.sessionId()),record.sessionId(),prepared.tokenId(),Instant.ofEpochSecond(prepared.expiresAtEpochSeconds()),replacement,record.absoluteExpiresAt(),record.principal(),record.admin(),record.attorney()));
        });
    }
    public boolean validate(VerifiedAuthToken token){
        return token.isSessionBound()?validator.isValid(token):legacy.permits(token)&&!legacyRevocations.isRevoked(token.tokenId());
    }
    public String refresh(VerifiedAuthToken current){
        if(!validate(current))throw new SecurityException("Authentication is unavailable.");
        var next=tokens.prepare(current.principal()); UUID sid;
        if(current.isSessionBound()){
            sid=UUID.fromString(current.sessionId());
            Instant accessExpiry=Instant.ofEpochSecond(next.expiresAtEpochSeconds());
            Instant sessionExpiry=sessions.find(current.principal(),sid).map(UserSessionView::expiresAt).filter(e->e.isAfter(accessExpiry)).orElse(accessExpiry);
            sessions.rotate(current.principal(),sid,UUID.fromString(current.tokenId()),next.tokenId(),sessionExpiry);
        }else{
            var created=sessions.create(current.principal(),ClientType.WEB,null,next.tokenId(),Instant.ofEpochSecond(next.expiresAtEpochSeconds()));sid=created.sessionId();
            legacyRevocations.revoke(current.tokenId(),current.expiresAtEpochSeconds());
        }
        return tokens.issueBound(next,sid);
    }
    public void logout(VerifiedAuthToken current){
        if(current.isSessionBound())sessions.revoke(current.principal(),UUID.fromString(current.sessionId()),"USER_LOGOUT");
        legacyRevocations.revoke(current.tokenId(),current.expiresAtEpochSeconds());
    }
    public record IssuedSession(String accessToken,UUID sessionId,UUID currentAccessJti,Instant expiresAt){}
    public record RememberedSession(String accessToken,UUID sessionId,UUID currentAccessJti,Instant expiresAt,String rememberCredential,Instant rememberExpiresAt,ServerPrincipal principal,boolean admin,boolean attorney){
        private static final java.security.SecureRandom RANDOM=new java.security.SecureRandom();
        static String newSecret(){byte[] b=new byte[32];RANDOM.nextBytes(b);return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(b);}
        static boolean valid(String value){return value!=null&&value.length()>=40&&value.length()<=128;}
        static byte[] hash(String value){try{return java.security.MessageDigest.getInstance("SHA-256").digest(value.getBytes(java.nio.charset.StandardCharsets.US_ASCII));}catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}}
    }
}
