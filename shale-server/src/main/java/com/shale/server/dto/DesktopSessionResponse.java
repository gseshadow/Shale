package com.shale.server.dto;

import java.time.Instant;
import java.util.UUID;
import com.shale.server.runtime.ServerAuthSessionService;

public record DesktopSessionResponse(boolean enrolled,String tokenType,String accessToken,UUID sessionId,
        Instant expiresAt,UUID currentJti) {
    public static DesktopSessionResponse from(ServerAuthSessionService.IssuedSession value){
        return new DesktopSessionResponse(true,"Bearer",value.accessToken(),value.sessionId(),value.expiresAt(),value.currentAccessJti());
    }
}
