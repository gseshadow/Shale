package com.shale.server.dto;

import java.time.Instant;
import java.util.UUID;
import com.shale.core.dto.AdminApplicationInstanceView;

public record AdminApplicationInstanceResponse(long applicationInstanceId,int userId,String userDisplayName,
		String userEmail,UUID machineId,String clientType,String applicationVersion,Instant startedAt,Instant endedAt,
		Instant lastHeartbeatAt,Instant lastHumanActivityAt) {
	public static AdminApplicationInstanceResponse from(AdminApplicationInstanceView v){return new AdminApplicationInstanceResponse(v.applicationInstanceId(),v.userId(),v.userDisplayName(),v.userEmail(),v.machineId(),v.clientType().name(),v.applicationVersion().toString(),v.startedAt(),v.endedAt(),v.lastHeartbeatAt(),v.lastHumanActivityAt());}
}
