package com.shale.core.service;

import java.time.Instant;
import com.shale.core.dto.ApplicationPolicyView;
import com.shale.core.model.ApplicationUpdatePolicyState;
import com.shale.core.model.ReleaseChannel;
import com.shale.core.model.SemanticVersion;

/** Converts validated policy data into a client presentation state without enforcing access. */
public final class ApplicationUpdatePolicyResolver {
	public ApplicationUpdatePolicyState resolve(SemanticVersion running, ReleaseChannel channel,
			ApplicationPolicyView policy, Instant authoritativeTime) {
		if (running == null || channel == null || policy == null || policy.channel() != channel)
			return ApplicationUpdatePolicyState.UNKNOWN;
		var allowed = policy.minimumAllowed();
		if (allowed != null && running.compareTo(allowed.version()) < 0) {
			Instant deadline = policy.requiredUpdateDeadline();
			if (deadline == null || authoritativeTime == null) return ApplicationUpdatePolicyState.UNKNOWN;
			return authoritativeTime.isBefore(deadline)
					? ApplicationUpdatePolicyState.REQUIRED_BEFORE_DEADLINE
					: ApplicationUpdatePolicyState.REQUIRED_DEADLINE_REACHED;
		}
		var recommended = policy.minimumRecommended();
		if (recommended != null && running.compareTo(recommended.version()) < 0)
			return ApplicationUpdatePolicyState.RECOMMENDED;
		return ApplicationUpdatePolicyState.CURRENT;
	}
}
