package com.shale.core.update;

import static org.junit.jupiter.api.Assertions.*;

import java.time.ZonedDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

final class UnattendedUpdateRetryPolicyTest {
	private final UnattendedUpdateRetryPolicy policy = new UnattendedUpdateRetryPolicy();

	@Test void evaluationsAndRealHandoffsAreBoundedPerWindow() {
		var now = time("2026-10-01T02:00:00-04:00[America/New_York]");
		var state = UnattendedUpdateRetryPolicy.State.empty(now);
		for (int i = 0; i < UnattendedUpdateRetryPolicy.MAX_EVALUATIONS_PER_WINDOW; i++) state = policy.forEvaluation(state, now);
		assertFalse(policy.mayEvaluate(state));
		assertEquals(UnattendedUpdateRetryPolicy.MAX_EVALUATIONS_PER_WINDOW, policy.forEvaluation(state, now).evaluations());
		state = policy.forHandoff(state);
		assertFalse(policy.mayHandoff(state), "a failed real attempt must not loop again that night");
		assertEquals(1, policy.forHandoff(state).handoffs());
	}

	@Test void localDateOrTimezoneChangeStartsANewWindowWithoutAddingTwentyFourHours() {
		var first = time("2026-10-01T03:00:00-04:00[America/New_York]");
		var exhausted = new UnattendedUpdateRetryPolicy.State(UnattendedUpdateRetryPolicy.WindowId.at(first), 4, 1);
		var nextDay = policy.forEvaluation(exhausted, time("2026-10-02T02:00:00-04:00[America/New_York]"));
		assertEquals(1, nextDay.evaluations()); assertEquals(0, nextDay.handoffs());
		var changedZone = policy.forEvaluation(exhausted, time("2026-10-01T02:00:00-07:00[America/Los_Angeles]"));
		assertEquals(1, changedZone.evaluations()); assertEquals(0, changedZone.handoffs());
	}

	@Test void schedulerStateContainsOnlyLocalWindowAndBoundedCounters() {
		var names = java.util.Arrays.stream(UnattendedUpdateRetryPolicy.State.class.getRecordComponents())
				.map(java.lang.reflect.RecordComponent::getName).toList();
		assertEquals(List.of("window", "evaluations", "handoffs"), names);
		for (String forbidden : List.of("user", "tenant", "email", "jwt", "jti", "password", "machine", "activity"))
			assertFalse(names.stream().anyMatch(name -> name.toLowerCase().contains(forbidden)), forbidden);
	}

	private static ZonedDateTime time(String value) { return ZonedDateTime.parse(value); }
}
