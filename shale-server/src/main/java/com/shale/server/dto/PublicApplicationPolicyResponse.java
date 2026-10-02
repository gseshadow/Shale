package com.shale.server.dto;

import java.time.Instant;
import com.shale.core.model.ReleaseChannel;

/** Deliberately excludes database ids, tenant/user/session context, access mode, and administrative metadata. */
public record PublicApplicationPolicyResponse(ReleaseChannel channel, long revision,
		String recommendedVersion, String minimumAllowedVersion, Instant deadline, Instant serverTime) {}
