package com.shale.core.model;

/** Presentation-only update policy result. Enforcement belongs to Phase 11B. */
public enum ApplicationUpdatePolicyState {
	CURRENT, RECOMMENDED, REQUIRED_BEFORE_DEADLINE, REQUIRED_DEADLINE_REACHED, UNKNOWN
}
