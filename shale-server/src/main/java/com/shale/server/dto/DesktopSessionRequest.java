package com.shale.server.dto;

/** Credentials are used once over TLS and are never persisted by this contract. */
public record DesktopSessionRequest(String email,String password,Long applicationInstanceId,Boolean remember,java.util.UUID installationId) {}
