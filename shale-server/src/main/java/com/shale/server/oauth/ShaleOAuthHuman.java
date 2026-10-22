package com.shale.server.oauth;

import com.shale.core.model.ClientType;
import com.shale.server.runtime.*;
import java.security.Principal;
import java.time.Instant;
import java.util.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** Bridge to existing durable WEB authority. No Spring account, credentials or session is created. */
public final class ShaleOAuthHuman {
    public record Identity(int tenant, int user, UUID session) implements Principal {
        @Override public String getName() { return tenant+":"+user+":"+session; }
        @Override public String toString() { return "ShaleOAuthHuman[redacted]"; }
    }
    private final ShaleAuthTokenService tokens;
    private final ServerAuthSessionService auth;
    private final DurableSessionStore sessions;
    public ShaleOAuthHuman(ShaleAuthTokenService tokens, ServerAuthSessionService auth, DurableSessionStore sessions) {
        this.tokens=tokens; this.auth=auth; this.sessions=sessions;
    }
    public Identity authenticate(HttpServletRequest request) {
        String raw;
        if(Collections.list(request.getHeaders("Authorization")).size()!=1) throw denied();
        try { raw=BearerTokenServerSessionResolver.bearerToken(request); }
        catch(org.springframework.web.server.ResponseStatusException invalid) { throw denied(); }
        if(raw==null || raw.length()>8192) throw denied();
        var token=tokens.verifyToken(raw)
                .filter(VerifiedAuthToken::isSessionBound).filter(auth::validate).orElseThrow(ShaleOAuthHuman::denied);
        var sid=UUID.fromString(token.sessionId());
        var session=sessions.find(token.principal(),sid).filter(s->s.clientType()==ClientType.WEB && s.isActive(Instant.now()))
                .filter(s->s.currentAccessJti().toString().equals(token.tokenId())).orElseThrow(ShaleOAuthHuman::denied);
        return new Identity(token.principal().shaleClientId(),token.principal().userId(),session.sessionId());
    }
    static Authentication authentication(Identity identity) {
        return UsernamePasswordAuthenticationToken.authenticated(identity,"",List.of());
    }
    static Identity current() {
        var auth=SecurityContextHolder.getContext().getAuthentication();
        if(auth==null || !auth.isAuthenticated() || !(auth.getPrincipal() instanceof Identity human)) throw denied();
        return human;
    }
    private static SecurityException denied() { return new SecurityException("Existing durable WEB authentication required."); }
}
