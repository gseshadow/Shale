package com.shale.desktop.session;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/** Process-local bearer authority. Phase 7C intentionally provides no restart/remember-me persistence. */
public final class DesktopServerSession {
    public record Credential(String accessToken,UUID sessionId,UUID currentJti,Instant expiresAt){
        public Credential{if(accessToken==null||accessToken.isBlank())throw new IllegalArgumentException("accessToken");java.util.Objects.requireNonNull(sessionId);java.util.Objects.requireNonNull(currentJti);java.util.Objects.requireNonNull(expiresAt);}
    }
    private final AtomicReference<Credential> current=new AtomicReference<>();
    public Optional<Credential> current(){return Optional.ofNullable(current.get());}
    public void install(Credential credential){current.set(java.util.Objects.requireNonNull(credential));}
    public void clear(){current.set(null);}
    public Optional<String> bearerToken(){return current().map(Credential::accessToken);}
}
