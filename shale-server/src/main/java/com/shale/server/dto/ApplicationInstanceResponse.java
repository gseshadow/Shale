package com.shale.server.dto;

import java.time.Instant;
import java.util.UUID;
import com.shale.core.dto.ApplicationInstanceView;
import com.shale.core.model.ClientType;

public record ApplicationInstanceResponse(long id, UUID machineId, ClientType clientType,
		String applicationVersion, Instant startedAt, Instant endedAt, Instant lastHeartbeatAt,
		Instant lastHumanActivityAt) {
	public static ApplicationInstanceResponse from(ApplicationInstanceView view){return new ApplicationInstanceResponse(view.id(),view.machineId(),view.clientType(),view.applicationVersion().toString(),view.startedAt(),view.endedAt(),view.lastHeartbeatAt(),view.lastHumanActivityAt());}
}
