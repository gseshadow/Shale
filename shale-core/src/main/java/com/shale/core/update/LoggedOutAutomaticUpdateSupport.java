package com.shale.core.update;

import java.util.Set;

/**
 * Phase 13D capability boundary for execution without the install owner's interactive session.
 *
 * <p>This is intentionally a closed, safe-default contract rather than a scheduler factory. A later
 * phase must replace the architecture decision explicitly before any task, service, or helper may be
 * registered.</p>
 */
public final class LoggedOutAutomaticUpdateSupport {
	public enum Decision { UNSUPPORTED }

	public enum Blocker {
		INSTALL_OWNER_PRINCIPAL_UNPROVEN,
		AUTHORITATIVE_POLICY_UNAVAILABLE,
		INSTALL_REGISTRATION_UNAVAILABLE,
		RUNTIME_VERSION_SOURCE_UNAVAILABLE,
		ATTEMPT_STORE_OWNERSHIP_UNPROVEN,
		EXECUTABLE_AUTHENTICITY_UNPROVEN,
		UNINSTALL_LIFECYCLE_UNPROVEN
	}

	public record Assessment(Decision decision, Set<Blocker> blockers) {
		public Assessment {
			blockers = Set.copyOf(blockers);
		}
	}

	private static final Assessment CURRENT = new Assessment(Decision.UNSUPPORTED, Set.of(Blocker.values()));

	private LoggedOutAutomaticUpdateSupport() {}

	public static Assessment current() {
		return CURRENT;
	}
}
