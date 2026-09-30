package com.shale.core.update;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.shale.core.model.ApplicationUpdatePolicyState;
import com.shale.core.model.ReleaseChannel;
import com.shale.core.model.SemanticVersion;
import com.shale.core.update.UnattendedUpdateEligibility.Decision;

final class UnattendedUpdateEligibilityTest {
	private final UnattendedUpdateEligibility resolver = new UnattendedUpdateEligibility();

	@Test void onlyExplicitEnabledPreferencePassesTheFirstGate() {
		for (var status : WorkstationUpdatePreference.Status.values()) {
			Decision expected = status == WorkstationUpdatePreference.Status.ENABLED ? Decision.ELIGIBLE : Decision.DEFER_PREFERENCE;
			assertEquals(expected, resolver.resolve(inputs(status, ApplicationUpdatePolicyState.RECOMMENDED)), status.name());
		}
	}

	@Test void recommendedAndBothRequiredStatesAreEligibleButCurrentAndUnknownAreNot() {
		for (var state : ApplicationUpdatePolicyState.values()) {
			Decision expected = switch (state) {
				case RECOMMENDED, REQUIRED_BEFORE_DEADLINE, REQUIRED_DEADLINE_REACHED -> Decision.ELIGIBLE;
				case CURRENT, UNKNOWN -> Decision.DEFER_POLICY;
			};
			assertEquals(expected, resolver.resolve(inputs(WorkstationUpdatePreference.Status.ENABLED, state)), state.name());
		}
	}

	@Test void availabilitySafetyAndPlatformProduceSpecificDeferrals() {
		assertEquals(Decision.DEFER_NO_UPDATE, resolver.resolve(with(inputs(), available("1.0.130", "1.0.130", "1.0.130"), null, null, null, null, null)));
		assertEquals(Decision.DEFER_UNAVAILABLE, resolver.resolve(with(inputs(), UnattendedUpdateEligibility.Availability.unavailable(), null, null, null, null, null)));
		assertEquals(Decision.DEFER_UNAVAILABLE, resolver.resolve(with(inputs(), incompatible(), null, null, null, null, null)));
		assertEquals(Decision.DEFER_ACTIVE_USER, resolver.resolve(with(inputs(), null, true, null, null, null, null)));
		assertEquals(Decision.DEFER_ACTIVE_WORK, resolver.resolve(with(inputs(), null, null, true, null, null, null)));
		assertEquals(Decision.DEFER_ACTIVE_WORK, resolver.resolve(with(inputs(), null, null, null, false, null, null)));
		assertEquals(Decision.DEFER_ALREADY_RUNNING, resolver.resolve(with(inputs(), null, null, null, null, false, null)));
		assertEquals(Decision.UNSUPPORTED, resolver.resolve(with(inputs(), null, null, null, null, null, false)));
	}

	@Test void windowRecentActivityAndMissingActivityDeferConservatively() {
		var input = inputs();
		var outside = new UnattendedUpdateEligibility.Inputs(input.preference(), input.policy(), input.availability(),
				ZonedDateTime.parse("2026-10-01T04:00:00-04:00[America/New_York]"), input.lastForegroundActivity(),
				false, false, true, true, true);
		assertEquals(Decision.DEFER_OUTSIDE_WINDOW, resolver.resolve(outside));
		var recent = new UnattendedUpdateEligibility.Inputs(input.preference(), input.policy(), input.availability(),
				input.workstationTime(), Optional.of(input.workstationTime().toInstant().minusSeconds(1799)),
				false, false, true, true, true);
		assertEquals(Decision.DEFER_ACTIVE_USER, resolver.resolve(recent));
		var unknown = new UnattendedUpdateEligibility.Inputs(input.preference(), input.policy(), input.availability(),
				input.workstationTime(), Optional.empty(), false, false, true, true, true);
		assertEquals(Decision.DEFER_ACTIVE_USER, resolver.resolve(unknown));
	}

	@Test void localWindowBoundariesAreStartInclusiveAndEndExclusive() {
		assertWindow("2026-10-01T01:59:59-04:00[America/New_York]", false);
		assertWindow("2026-10-01T02:00:00-04:00[America/New_York]", true);
		assertWindow("2026-10-01T03:59:59-04:00[America/New_York]", true);
		assertWindow("2026-10-01T04:00:00-04:00[America/New_York]", false);
		assertWindow("2026-10-01T04:00:01-04:00[America/New_York]", false);
	}

	@Test void dstAndTimezoneAreEvaluatedFromCurrentLocalCalendarTime() {
		// New York repeats 01:30 (outside the window); 02:30 occurs once. No fixed 24-hour arithmetic is used.
		assertEquals(false, UnattendedUpdateEligibility.insideWindow(ZonedDateTime.parse("2026-11-01T01:30:00-04:00[America/New_York]")));
		assertEquals(false, UnattendedUpdateEligibility.insideWindow(ZonedDateTime.parse("2026-11-01T01:30:00-05:00[America/New_York]")));
		assertEquals(true, UnattendedUpdateEligibility.insideWindow(ZonedDateTime.parse("2026-11-01T02:30:00-05:00[America/New_York]")));
		// On spring-forward day the missing 02:xx has no instant; the real 03:00 remains inside the window.
		assertEquals(true, UnattendedUpdateEligibility.insideWindow(ZonedDateTime.parse("2026-03-08T03:00:00-04:00[America/New_York]")));
		assertEquals(true, UnattendedUpdateEligibility.insideWindow(ZonedDateTime.parse("2026-10-01T02:30:00-07:00[America/Los_Angeles]")));
		assertEquals(false, UnattendedUpdateEligibility.insideWindow(ZonedDateTime.parse("2026-10-01T05:30:00+02:00[Europe/Berlin]")));
	}

	@Test void idleThresholdIsConservativeAndUsesInstantsAcrossClockTransitions() {
		Instant now = Instant.parse("2026-11-01T07:30:00Z");
		assertEquals(false, UnattendedUpdateEligibility.idle(now, Optional.empty()), "absence is not proof of idle");
		assertEquals(false, UnattendedUpdateEligibility.idle(now, Optional.of(now.minusSeconds(1799))));
		assertEquals(true, UnattendedUpdateEligibility.idle(now, Optional.of(now.minusSeconds(1800))));
		assertEquals(true, UnattendedUpdateEligibility.idle(now, Optional.of(now.minusSeconds(1801))));
		assertEquals(false, UnattendedUpdateEligibility.idle(now, Optional.of(now.plusSeconds(1))), "clock reversal must fail safe");
	}

	private void assertWindow(String value, boolean expected) {
		assertEquals(expected, UnattendedUpdateEligibility.insideWindow(ZonedDateTime.parse(value)), value);
	}

	private static UnattendedUpdateEligibility.Inputs inputs() {
		return inputs(WorkstationUpdatePreference.Status.ENABLED, ApplicationUpdatePolicyState.RECOMMENDED);
	}

	private static UnattendedUpdateEligibility.Inputs inputs(WorkstationUpdatePreference.Status preference,
			ApplicationUpdatePolicyState policy) {
		ZonedDateTime now = ZonedDateTime.parse("2026-10-01T03:00:00-04:00[America/New_York]");
		return new UnattendedUpdateEligibility.Inputs(new WorkstationUpdatePreference(preference), policy,
				available("1.0.129", "1.0.130", "1.0.130"), now, Optional.of(now.toInstant().minusSeconds(1800)), false, false,
				true, true, true);
	}

	private static UnattendedUpdateEligibility.Inputs with(UnattendedUpdateEligibility.Inputs i,
			UnattendedUpdateEligibility.Availability availability, Boolean foreground, Boolean activeWork, Boolean shutdown,
			Boolean lock, Boolean platform) {
		return new UnattendedUpdateEligibility.Inputs(i.preference(), i.policy(),
				availability == null ? i.availability() : availability, i.workstationTime(), i.lastForegroundActivity(),
				foreground == null ? i.foregroundVisible() : foreground,
				activeWork == null ? i.activeWork() : activeWork,
				shutdown == null ? i.cooperativeShutdownAvailable() : shutdown,
				lock == null ? i.updateLockAvailable() : lock,
				platform == null ? i.windowsCapabilityAvailable() : platform);
	}

	private static UnattendedUpdateEligibility.Availability available(String current, String target, String policyTarget) {
		return new UnattendedUpdateEligibility.Availability(true, SemanticVersion.parse(current), SemanticVersion.parse(target),
				SemanticVersion.parse(policyTarget), ReleaseChannel.PRODUCTION, ReleaseChannel.PRODUCTION, true, true);
	}

	private static UnattendedUpdateEligibility.Availability incompatible() {
		return new UnattendedUpdateEligibility.Availability(true, SemanticVersion.parse("1.0.129"), SemanticVersion.parse("1.0.131"),
				SemanticVersion.parse("1.0.130"), ReleaseChannel.PRODUCTION, ReleaseChannel.PRODUCTION, true, true);
	}
}
