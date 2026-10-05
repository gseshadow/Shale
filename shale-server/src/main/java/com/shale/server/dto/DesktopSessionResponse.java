package com.shale.server.dto;

import java.time.Instant;
import java.util.UUID;
import com.shale.server.runtime.ServerAuthSessionService;

public record DesktopSessionResponse(boolean enrolled,String tokenType,String accessToken,UUID sessionId,
        Instant expiresAt,UUID currentJti,String rememberCredential,Instant rememberExpiresAt,
        Integer userId,Integer shaleClientId,String email,Boolean admin,Boolean attorney) {
    public static DesktopSessionResponse from(ServerAuthSessionService.IssuedSession value){
        return new DesktopSessionResponse(true,"Bearer",value.accessToken(),value.sessionId(),value.expiresAt(),value.currentAccessJti(),null,null,null,null,null,null,null);
    }
    public static DesktopSessionResponse from(ServerAuthSessionService.RememberedSession value,boolean admin,boolean attorney){var p=value.principal();return new DesktopSessionResponse(true,"Bearer",value.accessToken(),value.sessionId(),value.expiresAt(),value.currentAccessJti(),value.rememberCredential(),value.rememberExpiresAt(),p.userId(),p.shaleClientId(),p.email(),admin||value.admin(),attorney||value.attorney());}
}
