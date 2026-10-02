package com.shale.server.runtime;

import java.time.Instant;
import java.util.UUID;
import com.shale.core.dto.UserSessionView;
import com.shale.core.model.ClientType;

/** Centralizes bound JWT issuance, legacy upgrade, conditional rotation, and durable logout. */
public final class ServerAuthSessionService {
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
    public boolean validate(VerifiedAuthToken token){
        return token.isSessionBound()?validator.isValid(token):legacy.permits(token)&&!legacyRevocations.isRevoked(token.tokenId());
    }
    public String refresh(VerifiedAuthToken current){
        if(!validate(current))throw new SecurityException("Authentication is unavailable.");
        var next=tokens.prepare(current.principal()); UUID sid;
        if(current.isSessionBound()){
            sid=UUID.fromString(current.sessionId());
            sessions.rotate(current.principal(),sid,UUID.fromString(current.tokenId()),next.tokenId(),Instant.ofEpochSecond(next.expiresAtEpochSeconds()));
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
}
