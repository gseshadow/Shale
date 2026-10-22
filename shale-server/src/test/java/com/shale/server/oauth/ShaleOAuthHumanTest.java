package com.shale.server.oauth;

import com.shale.core.dto.UserSessionView;
import com.shale.core.model.ClientType;
import com.shale.server.runtime.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import static org.junit.jupiter.api.Assertions.*;

class ShaleOAuthHumanTest {
    @Test void onlyLiveDurableWebSessionCanSupplyHumanAndNoCallerIdentityIsUsed() {
        var tokens=new ShaleAuthTokenService("synthetic-human-bridge-secret-at-least-32-bytes",3600,Clock.systemUTC());
        var principal=new ServerPrincipal(72,7,"not-authority@example.invalid");
        var prepared=tokens.prepare(principal);UUID sid=UUID.randomUUID();
        String bound=tokens.issueBound(prepared,sid);
        var store=new FixtureSessions();
        var revocations=new InMemoryTokenRevocationStore();
        var service=new ServerAuthSessionService(tokens,store,new DurableSessionTokenValidator(store),new LegacyTokenCompatibilityPolicy(Instant.EPOCH,3600,Clock.systemUTC()),revocations);
        var bridge=new ShaleOAuthHuman(tokens,service,store);
        var request=new MockHttpServletRequest();request.addHeader("Authorization","Bearer "+bound);
        request.addParameter("user_id","73");request.addHeader("X-Shale-TenantId","8");
        for(var type:List.of(ClientType.DESKTOP,ClientType.MOBILE)) {
            store.current=new UserSessionView(1,sid,type,null,prepared.tokenId(),Instant.now(),Instant.now().plusSeconds(3600),null,null,null);
            assertThrows(SecurityException.class,()->bridge.authenticate(request),"Non-WEB sessions cannot consent");
        }
        store.current=new UserSessionView(1,sid,ClientType.WEB,null,prepared.tokenId(),Instant.now(),Instant.now().plusSeconds(3600),null,null,null);
        var human=bridge.authenticate(request);assertEquals(72,human.user());assertEquals(7,human.tenant());assertEquals(sid,human.session());
        var legacy=new MockHttpServletRequest();legacy.addHeader("Authorization","Bearer "+tokens.issue(principal));
        assertThrows(SecurityException.class,()->bridge.authenticate(legacy));
        store.current=null;assertThrows(SecurityException.class,()->bridge.authenticate(request),"Durable revocation/deletion must deny even with a valid JWT");
    }
    static final class FixtureSessions implements DurableSessionStore {
        UserSessionView current;
        @Override public Optional<UserSessionView> find(ServerPrincipal p,UUID sid) { return current!=null && current.sessionId().equals(sid) ? Optional.of(current):Optional.empty(); }
        @Override public UserSessionView create(ServerPrincipal p,ClientType type,Long instance,UUID jti,Instant expires) { throw new UnsupportedOperationException(); }
        @Override public UserSessionView rotate(ServerPrincipal p,UUID sid,UUID expected,UUID next,Instant expiry) { throw new UnsupportedOperationException(); }
        @Override public UserSessionView revoke(ServerPrincipal p,UUID sid,String reason) { throw new UnsupportedOperationException(); }
    }
}
