package com.shale.core.update;

/** A fail-safe, machine-scoped answer for future unattended update execution. */
public record WorkstationUpdatePreference(Status status) {
	public enum Status { ENABLED, DISABLED, MISSING, UNAVAILABLE, CORRUPT }

	public boolean unattendedExecutionPermitted() {
		return status == Status.ENABLED;
	}
}
