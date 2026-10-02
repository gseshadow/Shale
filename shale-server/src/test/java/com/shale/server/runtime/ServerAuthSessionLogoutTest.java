package com.shale.server.runtime;

import static org.junit.jupiter.api.Assertions.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import com.shale.core.dto.UserSessionView;
import com.shale.core.model.ClientType;

class ServerAuthSessionLogoutTest {
    @Test void boundLogoutRevokesTheExactEnrolledSessionWithUserLogoutReason() {
        var principal=new ServerPrincipal(9,7,"owner@test");
        var store=new Store();
        var tokens=new ShaleAuthTokenService("test-auth-token-secret-that-is-long-enough",3600,Clock.systemUTC());
        var service=new ServerAuthSessionService(tokens,store,new DurableSessionTokenValidator(store),new LegacyTokenCompatibilityPolicy(Instant.now(),3600,Clock.systemUTC()),new InMemoryTokenRevocationStore());
        var issued=service.issueDesktop(principal,44L);

        service.logout(tokens.verifyToken(issued.accessToken()).orElseThrow());

        assertEquals(issued.sessionId(),store.revokedSession,"logout must revoke the sid enrolled into the presented bearer");
        assertEquals(principal,store.revokedPrincipal,"logout must retain token-derived tenant and user ownership");
        assertEquals("USER_LOGOUT",store.reason);
        assertNotNull(store.value.revokedAt(),"the durable session must receive RevokedAt");
        assertFalse(store.value.isActive(Instant.now()),"an active-only listing must exclude the revoked row");
    }

    private static final class Store implements DurableSessionStore {
        UserSessionView value;UUID revokedSession;ServerPrincipal revokedPrincipal;String reason;
        public UserSessionView create(ServerPrincipal p,ClientType c,Long i,UUID j,Instant e){return value=new UserSessionView(1,UUID.randomUUID(),c,i,j,Instant.now(),e,null,null,null);}
        public Optional<UserSessionView> find(ServerPrincipal p,UUID s){return Optional.ofNullable(value).filter(v->v.sessionId().equals(s));}
        public UserSessionView rotate(ServerPrincipal p,UUID s,UUID x,UUID n,Instant e){throw new UnsupportedOperationException();}
        public UserSessionView revoke(ServerPrincipal p,UUID s,String r){revokedPrincipal=p;revokedSession=s;reason=r;return value=new UserSessionView(value.id(),value.sessionId(),value.clientType(),value.applicationInstanceId(),value.currentAccessJti(),value.issuedAt(),value.expiresAt(),value.lastRefreshedAt(),Instant.now(),r);}
    }
}
