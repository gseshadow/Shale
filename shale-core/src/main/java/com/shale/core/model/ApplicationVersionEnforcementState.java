package com.shale.core.model;

/** Operational minimum-version state. This is intentionally separate from update-policy presentation. */
public enum ApplicationVersionEnforcementState {
	ALLOWED,
	DRAINING_REQUIRED_UPDATE,
	BLOCKED_NEW_WORK,
	UNKNOWN_GRACE,
	RECOVERING
}
