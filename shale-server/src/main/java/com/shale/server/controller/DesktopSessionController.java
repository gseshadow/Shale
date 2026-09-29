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
    public DesktopSessionController(AuthServicePort authentication,ServerAuthSessionService sessions,
            DesktopApplicationInstanceVerifier instances){this.authentication=Objects.requireNonNull(authentication);this.sessions=Objects.requireNonNull(sessions);this.instances=Objects.requireNonNull(instances);}
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
        return ResponseEntity.ok(DesktopSessionResponse.from(sessions.issueDesktop(principal,instance)));
    }
}
