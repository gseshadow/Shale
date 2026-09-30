package com.shale.core.update;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.EnumSet;

import org.junit.jupiter.api.Test;

final class LoggedOutAutomaticUpdateSupportTest {
	@Test void phase13dCannotAccidentallyClaimLoggedOutCapability() {
		var assessment = LoggedOutAutomaticUpdateSupport.current();

		assertEquals(LoggedOutAutomaticUpdateSupport.Decision.UNSUPPORTED, assessment.decision(),
				"logged-out execution must remain unsupported until every security blocker has a proven design");
		assertEquals(EnumSet.allOf(LoggedOutAutomaticUpdateSupport.Blocker.class), assessment.blockers(),
				"the capability boundary must retain every Phase 13D blocker");
	}

	@Test void callerCannotRemoveBlockersFromTheSharedAssessment() {
		var blockers = LoggedOutAutomaticUpdateSupport.current().blockers();
		assertFalse(blockers.isEmpty());
		org.junit.jupiter.api.Assertions.assertThrows(UnsupportedOperationException.class,
				() -> blockers.remove(LoggedOutAutomaticUpdateSupport.Blocker.AUTHORITATIVE_POLICY_UNAVAILABLE));
	}
}
