package com.shale.core.dto;

import java.time.Instant;
import com.shale.core.model.SemanticVersion;

/** Counts process launches and distinct users, not active-user adoption. */
public record ApplicationVersionDistributionView(SemanticVersion applicationVersion, long instanceCount,
		long distinctUserCount, Instant latestHeartbeatAt) { }
