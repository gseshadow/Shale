package com.shale.core.update;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.shale.core.model.ApplicationUpdatePolicyState;
import com.shale.core.model.ReleaseChannel;
import com.shale.core.model.SemanticVersion;

final class UnattendedUpdateEvaluationServiceTest {
	@Test void deferralsNeverCrossThePhase12HandoffBoundary() {
		for (var status : WorkstationUpdatePreference.Status.values()) {
			if (status == WorkstationUpdatePreference.Status.ENABLED) continue;
			AtomicInteger handoffs = new AtomicInteger();
			var service = new UnattendedUpdateEvaluationService(() -> new WorkstationUpdatePreference(status));
			assertEquals(UnattendedUpdateEligibility.Decision.DEFER_PREFERENCE,
					service.evaluateAndHandoff(UnattendedUpdateEvaluationServiceTest::eligibleInputs, ignored -> handoffs.incrementAndGet()));
			assertEquals(0, handoffs.get(), "a deferral must not create an update attempt");
		}
	}

	@Test void providerIsRereadAndExactlyOneHandoffOccursOnlyWhenEligible() {
		AtomicInteger reads = new AtomicInteger(); AtomicInteger handoffs = new AtomicInteger();
		var service = new UnattendedUpdateEvaluationService(() -> new WorkstationUpdatePreference(
				reads.incrementAndGet() == 1 ? WorkstationUpdatePreference.Status.DISABLED : WorkstationUpdatePreference.Status.ENABLED));
		assertEquals(UnattendedUpdateEligibility.Decision.DEFER_PREFERENCE,
				service.evaluateAndHandoff(UnattendedUpdateEvaluationServiceTest::eligibleInputs, ignored -> handoffs.incrementAndGet()));
		assertEquals(UnattendedUpdateEligibility.Decision.ELIGIBLE,
				service.evaluateAndHandoff(UnattendedUpdateEvaluationServiceTest::eligibleInputs, ignored -> handoffs.incrementAndGet()));
		assertEquals(3, reads.get(), "eligible execution must reread Phase 13A again at the race recheck");
		assertEquals(1, handoffs.get(), "Phase 12 begins only at the real handoff seam");
	}

	@Test void safetyChangeAtImmediateRecheckPreventsHandoff() {
		AtomicInteger observations = new AtomicInteger(); AtomicInteger handoffs = new AtomicInteger();
		var service = new UnattendedUpdateEvaluationService(() ->
				new WorkstationUpdatePreference(WorkstationUpdatePreference.Status.ENABLED));
		assertEquals(UnattendedUpdateEligibility.Decision.DEFER_ACTIVE_USER,
				service.evaluateAndHandoff(() -> {
					var input = eligibleInputs();
					if (observations.incrementAndGet() == 1) return input;
					return new UnattendedUpdateEligibility.Inputs(input.preference(), input.policy(), input.availability(),
							input.workstationTime(), Optional.of(input.workstationTime().toInstant()), false, false, true, true, true);
				}, ignored -> handoffs.incrementAndGet()));
		assertEquals(0, handoffs.get(), "new human activity must win the handoff race");
	}

	private static UnattendedUpdateEligibility.Inputs eligibleInputs() {
		var now = ZonedDateTime.parse("2026-10-01T03:00:00-04:00[America/New_York]");
		return new UnattendedUpdateEligibility.Inputs(new WorkstationUpdatePreference(WorkstationUpdatePreference.Status.ENABLED),
				ApplicationUpdatePolicyState.RECOMMENDED, new UnattendedUpdateEligibility.Availability(true,
						SemanticVersion.parse("1.0.129"), SemanticVersion.parse("1.0.130"), SemanticVersion.parse("1.0.130"),
						ReleaseChannel.PRODUCTION, ReleaseChannel.PRODUCTION, true, true),
				now, Optional.of(now.toInstant().minusSeconds(1800)), false, false, true, true, true);
	}
}
