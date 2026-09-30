package com.shale.core.update;

/** Inspect-only aggregate answer. Only {@link #READY} permits an unattended shutdown request. */
public enum CooperativeShutdownReadiness {
	READY,
	ACTIVE_MUTATION_WORKFLOW,
	SAVE_IN_FLIGHT,
	PROMPT_REQUIRED,
	UNKNOWN;

	public boolean permitsUnattendedShutdown() {
		return this == READY;
	}
}
