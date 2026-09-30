package com.shale.core.update;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.Objects;
import java.util.Optional;

import com.shale.core.model.ApplicationUpdatePolicyState;
import com.shale.core.model.ReleaseChannel;
import com.shale.core.model.SemanticVersion;

/** Pure Phase 13B decision boundary. It does not schedule or launch an updater. */
public final class UnattendedUpdateEligibility {
	public static final LocalTime WINDOW_START = LocalTime.of(2, 0);
	public static final LocalTime WINDOW_END = LocalTime.of(4, 0);
	public static final Duration IDLE_THRESHOLD = Duration.ofMinutes(30);

	public record Availability(boolean manifestReachable, SemanticVersion currentVersion,
			SemanticVersion targetVersion, SemanticVersion policyTargetVersion, ReleaseChannel manifestChannel,
			ReleaseChannel policyChannel, boolean packagePresent, boolean packageCompatible) {
		public static Availability unavailable() {
			return new Availability(false, null, null, null, null, null, false, false);
		}
	}
	public enum Decision {
		ELIGIBLE,
		DEFER_PREFERENCE,
		DEFER_POLICY,
		DEFER_NO_UPDATE,
		DEFER_ACTIVE_USER,
		DEFER_ACTIVE_WORK,
		DEFER_OUTSIDE_WINDOW,
		DEFER_ALREADY_RUNNING,
		DEFER_UNAVAILABLE,
		UNSUPPORTED
	}

	public record Inputs(
			WorkstationUpdatePreference preference,
			ApplicationUpdatePolicyState policy,
			Availability availability,
			ZonedDateTime workstationTime,
			Optional<Instant> lastForegroundActivity,
			boolean foregroundVisible,
			boolean activeWork,
			boolean cooperativeShutdownAvailable,
			boolean updateLockAvailable,
			boolean windowsCapabilityAvailable) {
		public Inputs {
			Objects.requireNonNull(preference, "preference");
			Objects.requireNonNull(policy, "policy");
			Objects.requireNonNull(availability, "availability");
			Objects.requireNonNull(workstationTime, "workstationTime");
			Objects.requireNonNull(lastForegroundActivity, "lastForegroundActivity");
		}
	}

	public Decision resolve(Inputs input) {
		Objects.requireNonNull(input, "input");
		// Consent is deliberately first and only the provider's explicit ENABLED answer passes.
		if (!input.preference().unattendedExecutionPermitted()) return Decision.DEFER_PREFERENCE;
		if (!input.windowsCapabilityAvailable()) return Decision.UNSUPPORTED;
		if (!eligiblePolicy(input.policy())) return Decision.DEFER_POLICY;
		Decision availabilityDecision = availabilityDecision(input.availability());
		if (availabilityDecision != null) return availabilityDecision;
		if (!insideWindow(input.workstationTime())) return Decision.DEFER_OUTSIDE_WINDOW;
		if (!idle(input.workstationTime().toInstant(), input.lastForegroundActivity()) || input.foregroundVisible())
			return Decision.DEFER_ACTIVE_USER;
		if (input.activeWork() || !input.cooperativeShutdownAvailable()) return Decision.DEFER_ACTIVE_WORK;
		if (!input.updateLockAvailable()) return Decision.DEFER_ALREADY_RUNNING;
		return Decision.ELIGIBLE;
	}

	public static boolean insideWindow(ZonedDateTime workstationTime) {
		LocalTime local = workstationTime.toLocalTime();
		return !local.isBefore(WINDOW_START) && local.isBefore(WINDOW_END);
	}

	public static boolean idle(Instant now, Optional<Instant> lastActivity) {
		if (lastActivity.isEmpty() || lastActivity.orElseThrow().isAfter(now)) return false;
		return Duration.between(lastActivity.orElseThrow(), now).compareTo(IDLE_THRESHOLD) >= 0;
	}

	private static boolean eligiblePolicy(ApplicationUpdatePolicyState policy) {
		return policy == ApplicationUpdatePolicyState.RECOMMENDED
				|| policy == ApplicationUpdatePolicyState.REQUIRED_BEFORE_DEADLINE
				|| policy == ApplicationUpdatePolicyState.REQUIRED_DEADLINE_REACHED;
	}

	private static Decision availabilityDecision(Availability availability) {
		if (!availability.manifestReachable() || availability.currentVersion() == null
				|| availability.targetVersion() == null || availability.policyTargetVersion() == null
				|| availability.manifestChannel() == null || availability.policyChannel() == null)
			return Decision.DEFER_UNAVAILABLE;
		if (availability.targetVersion().compareTo(availability.currentVersion()) <= 0)
			return Decision.DEFER_NO_UPDATE;
		if (!availability.packagePresent() || !availability.packageCompatible()
				|| availability.manifestChannel() != availability.policyChannel()
				|| !availability.targetVersion().equals(availability.policyTargetVersion()))
			return Decision.DEFER_UNAVAILABLE;
		return null;
	}
}
