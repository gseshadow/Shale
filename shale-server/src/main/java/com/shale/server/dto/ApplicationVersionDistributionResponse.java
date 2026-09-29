package com.shale.server.dto;

import java.time.Instant;
import com.shale.core.dto.ApplicationVersionDistributionView;

public record ApplicationVersionDistributionResponse(String applicationVersion,long instanceCount,
		long distinctUserCount,Instant latestHeartbeatAt) {
	public static ApplicationVersionDistributionResponse from(ApplicationVersionDistributionView v){return new ApplicationVersionDistributionResponse(v.applicationVersion().toString(),v.instanceCount(),v.distinctUserCount(),v.latestHeartbeatAt());}
}
