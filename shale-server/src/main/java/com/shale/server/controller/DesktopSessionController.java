package com.shale.server.controller;

import java.util.Objects;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import com.shale.core.model.User;
import com.shale.core.service.AuthServicePort;
import com.shale.server.dto.*;
import com.shale.server.runtime.*;

/** Additive desktop-only credential exchange; it cannot create a session from asserted identifiers. */
@RestController
@org.springframework.context.annotation.Profile({"dev","local","prod","azure"})
public final class DesktopSessionController {
    private static final LoginErrorResponse INVALID=new LoginErrorResponse(false,"invalid_credentials","Invalid email or password.");
    private final AuthServicePort authentication;private final ServerAuthSessionService sessions;
    private final DesktopApplicationInstanceVerifier instances;
    private final RememberCredentialStore remembers;
    public DesktopSessionController(AuthServicePort authentication,ServerAuthSessionService sessions,
            DesktopApplicationInstanceVerifier instances){this(authentication,sessions,instances,new RememberCredentialStore(){public void create(ServerPrincipal p,java.util.UUID s,java.util.UUID i,byte[] h,java.time.Instant e){throw new UnsupportedOperationException();}public java.util.Optional<Record> rotate(byte[] p,java.util.UUID i,byte[] r,java.util.UUID j,java.time.Instant n){return java.util.Optional.empty();}public void deleteForSession(ServerPrincipal p,java.util.UUID s){}});}
    @org.springframework.beans.factory.annotation.Autowired
    public DesktopSessionController(AuthServicePort authentication,ServerAuthSessionService sessions,
            DesktopApplicationInstanceVerifier instances,RememberCredentialStore remembers){this.authentication=Objects.requireNonNull(authentication);this.sessions=Objects.requireNonNull(sessions);this.instances=Objects.requireNonNull(instances);this.remembers=Objects.requireNonNull(remembers);}
    @PostMapping("/api/auth/desktop-session")
    public ResponseEntity<?> enroll(@RequestBody DesktopSessionRequest request){
        if(request==null||request.email()==null||request.email().isBlank()||request.password()==null)
            return ResponseEntity.badRequest().body(new LoginErrorResponse(false,"invalid_request","Email and password are required."));
        var authenticated=authentication.authenticate(request.email().trim(),request.password());
        if(!authenticated.isOk())return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(INVALID);
        User user=authenticated.value().orElseThrow();
        ServerPrincipal principal=new ServerPrincipal(user.getId(),user.getShaleClientId(),user.getEmail());
        Long instance=request.applicationInstanceId();
        if(instance!=null&&!instances.isAttachable(principal,instance))
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new LoginErrorResponse(false,"instance_mismatch","Desktop session enrollment was rejected."));
        if(Boolean.TRUE.equals(request.remember())){
            if(request.installationId()==null)return ResponseEntity.badRequest().body(new LoginErrorResponse(false,"invalid_request","Installation identity is required for remembered sign-in."));
            return ResponseEntity.ok(DesktopSessionResponse.from(sessions.issueRememberedDesktop(principal,instance,request.installationId(),remembers),user.isAdmin(),user.isAttorney()));
        }
        return ResponseEntity.ok(DesktopSessionResponse.from(sessions.issueDesktop(principal,instance)));
    }

    @PostMapping("/api/auth/desktop-session/restore")
    public ResponseEntity<?> restore(@RequestBody DesktopRestoreRequest request){
        if(request==null||request.installationId()==null||request.credential()==null||request.replacementCredential()==null)return ResponseEntity.badRequest().body(new LoginErrorResponse(false,"invalid_request","Remembered sign-in request is incomplete."));
        var restored=sessions.restoreDesktop(request.credential(),request.replacementCredential(),request.installationId(),remembers);
        if(restored.isEmpty())return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new LoginErrorResponse(false,"remembered_sign_in_invalid","Your saved sign-in has expired or was revoked. Please sign in again."));
        var v=restored.get();return ResponseEntity.ok(DesktopSessionResponse.from(v,false,false));
    }
}
