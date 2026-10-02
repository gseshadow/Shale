package com.shale.server.runtime;

import static org.junit.jupiter.api.Assertions.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import com.shale.core.dto.UserSessionView;
import com.shale.core.model.ClientType;

class DurableSessionAuthTest {
    static final Instant NOW=Instant.parse("2026-09-29T12:00:00Z");
    static final ServerPrincipal PRINCIPAL=new ServerPrincipal(9,7,"user@test");
    @Test void boundTokenRequiresSharedDurableStateAndRotationInvalidatesOldJti(){
        var store=new MemoryStore();var tokens=new ShaleAuthTokenService("test-auth-token-secret-that-is-long-enough",3600,Clock.fixed(NOW,ZoneOffset.UTC));
        var validator=new DurableSessionTokenValidator(store,Clock.fixed(NOW,ZoneOffset.UTC));
        var service=new ServerAuthSessionService(tokens,store,validator,new LegacyTokenCompatibilityPolicy(NOW,3600,Clock.fixed(NOW,ZoneOffset.UTC)),new InMemoryTokenRevocationStore(Clock.fixed(NOW,ZoneOffset.UTC)));
        String first=service.issue(PRINCIPAL);var old=tokens.verifyToken(first).orElseThrow();
        assertNotNull(old.sessionId(),"new JWT must carry sid");assertEquals(UUID.fromString(old.tokenId()),store.value.currentAccessJti(),"persisted JTI must match JWT");assertTrue(service.validate(old));
        String replacement=service.refresh(old);var current=tokens.verifyToken(replacement).orElseThrow();
        assertEquals(old.sessionId(),current.sessionId(),"refresh must retain sid");assertFalse(service.validate(old),"rotated JTI must be stale across validators");assertTrue(new DurableSessionTokenValidator(store,Clock.fixed(NOW,ZoneOffset.UTC)).isValid(current),"a separate validator must observe shared state");assertNotNull(store.value.lastRefreshedAt());
        assertThrows(SecurityException.class,()->service.refresh(old),"a concurrent stale refresh must lose");
    }
    @Test void durableValidationRejectsMissingRevokedExpiredWrongOwnerAndMalformedClaims(){
        var store=new MemoryStore();UUID sid=UUID.randomUUID(),jti=UUID.randomUUID();store.value=view(sid,jti,PRINCIPAL,NOW.plusSeconds(60),null);
        var v=new DurableSessionTokenValidator(store,Clock.fixed(NOW,ZoneOffset.UTC));
        assertTrue(v.isValid(token(PRINCIPAL,jti,sid)));
        store.value=null;assertFalse(v.isValid(token(PRINCIPAL,jti,sid)));
        store.value=view(sid,jti,PRINCIPAL,NOW.plusSeconds(60),NOW);assertFalse(v.isValid(token(PRINCIPAL,jti,sid)));
        store.value=view(sid,jti,PRINCIPAL,NOW.minusSeconds(1),null);assertFalse(v.isValid(token(PRINCIPAL,jti,sid)));
        store.value=view(sid,jti,PRINCIPAL,NOW.plusSeconds(60),null);assertFalse(v.isValid(token(new ServerPrincipal(9,8,null),jti,sid)));assertFalse(v.isValid(token(new ServerPrincipal(10,7,null),jti,sid)));
        assertFalse(v.isValid(new VerifiedAuthToken(PRINCIPAL,jti.toString(),NOW.getEpochSecond(),NOW.plusSeconds(60).getEpochSecond(),"bad")));
    }
    @Test void legacyEligibilityIsBoundedAndRefreshUpgradesToDurableSession(){
        var store=new MemoryStore();var legacyTokens=new ShaleAuthTokenService("test-auth-token-secret-that-is-long-enough",3600,Clock.fixed(NOW.minusSeconds(10),ZoneOffset.UTC));String raw=legacyTokens.issue(PRINCIPAL);var legacy=legacyTokens.verifyToken(raw).orElseThrow();
        var revocations=new InMemoryTokenRevocationStore(Clock.fixed(NOW,ZoneOffset.UTC));var policy=new LegacyTokenCompatibilityPolicy(NOW,3600,Clock.fixed(NOW,ZoneOffset.UTC));
        var issuingTokens=new ShaleAuthTokenService("test-auth-token-secret-that-is-long-enough",3600,Clock.fixed(NOW,ZoneOffset.UTC));var service=new ServerAuthSessionService(issuingTokens,store,new DurableSessionTokenValidator(store,Clock.fixed(NOW,ZoneOffset.UTC)),policy,revocations);
        assertTrue(service.validate(legacy));var upgraded=issuingTokens.verifyToken(service.refresh(legacy)).orElseThrow();assertTrue(upgraded.isSessionBound());assertTrue(revocations.isRevoked(legacy.tokenId()));
        assertFalse(new LegacyTokenCompatibilityPolicy(NOW,3600,Clock.fixed(NOW.plusSeconds(3600),ZoneOffset.UTC)).permits(legacy),"window must close deterministically");
        assertFalse(new LegacyTokenCompatibilityPolicy(NOW.minusSeconds(20),3600,Clock.fixed(NOW,ZoneOffset.UTC)).permits(legacy),"post-cutover unbound token must fail");
    }
    private static VerifiedAuthToken token(ServerPrincipal p,UUID j,UUID s){return new VerifiedAuthToken(p,j.toString(),NOW.getEpochSecond(),NOW.plusSeconds(60).getEpochSecond(),s.toString());}
    private static UserSessionView view(UUID s,UUID j,ServerPrincipal p,Instant e,Instant r){return new UserSessionView(1,s,ClientType.WEB,null,j,NOW,e,null,r,r==null?null:"USER_LOGOUT");}
    static final class MemoryStore implements DurableSessionStore {UserSessionView value;
        public UserSessionView create(ServerPrincipal p,ClientType c,Long i,UUID j,Instant e){return value=view(UUID.randomUUID(),j,p,e,null);}
        public Optional<UserSessionView> find(ServerPrincipal p,UUID s){return value!=null&&value.sessionId().equals(s)&&p.equals(PRINCIPAL)?Optional.of(value):Optional.empty();}
        public UserSessionView rotate(ServerPrincipal p,UUID s,UUID x,UUID n,Instant e){if(value==null||!value.currentAccessJti().equals(x)||value.revokedAt()!=null||!NOW.isBefore(value.expiresAt()))throw new SecurityException();value=new UserSessionView(value.id(),s,value.clientType(),null,n,value.issuedAt(),e,NOW,null,null);return value;}
        public UserSessionView revoke(ServerPrincipal p,UUID s,String r){if(value.revokedAt()==null)value=new UserSessionView(value.id(),s,value.clientType(),null,value.currentAccessJti(),value.issuedAt(),value.expiresAt(),value.lastRefreshedAt(),NOW,r);return value;}
    }
}
