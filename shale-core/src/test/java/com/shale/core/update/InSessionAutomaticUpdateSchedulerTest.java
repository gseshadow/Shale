package com.shale.core.update;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.ArrayDeque;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import com.shale.core.model.ApplicationUpdatePolicyState;
import com.shale.core.model.ReleaseChannel;
import com.shale.core.model.SemanticVersion;

final class InSessionAutomaticUpdateSchedulerTest {
	@Test void lifecycleStartsOnceAndStaleGenerationCallbacksDoNothing() {
		var timer = new FakeTimer(); var handoffs = new AtomicInteger();
		var clock = new AtomicReference<>(time("2026-10-01T01:00:00-04:00[America/New_York]"));
		var scheduler = scheduler(clock, timer, handoffs, enabled());
		scheduler.start(); scheduler.start();
		assertEquals(1, timer.size(), "repeated authenticated initialization must own one timer");
		Runnable stale = timer.remove().task(); scheduler.stop(); stale.run();
		assertEquals(0, handoffs.get(), "a callback from a logged-out generation must be inert");
		assertFalse(scheduler.isRunning());
		scheduler.start(); assertEquals(1, timer.size(), "a new authenticated generation owns one new timer");
	}

	@Test void schedulesLocalWindowWithoutFixedTwentyFourHourArithmetic() {
		assertEquals(Duration.ofHours(1), InSessionAutomaticUpdateScheduler.delayUntilCandidate(
				time("2026-10-01T01:00:00-04:00[America/New_York]")));
		assertEquals(Duration.ZERO, InSessionAutomaticUpdateScheduler.delayUntilCandidate(
				time("2026-10-01T02:00:00-04:00[America/New_York]")));
		assertEquals(Duration.ofHours(22), InSessionAutomaticUpdateScheduler.delayUntilCandidate(
				time("2026-10-01T04:00:00-04:00[America/New_York]")));
		assertEquals(Duration.ofHours(22), InSessionAutomaticUpdateScheduler.delayUntilNextWindow(
				time("2026-03-07T04:00:00-05:00[America/New_York]")), "DST start uses the next local 02:00 identity");
	}

	@Test void resumeInsideWindowEvaluatesButLateTimerAfterWindowDefersToNextDay() {
		var timer = new FakeTimer(); var handoffs = new AtomicInteger();
		var clock = new AtomicReference<>(time("2026-10-01T01:00:00-04:00[America/New_York]"));
		var scheduler = scheduler(clock, timer, handoffs, enabled()); scheduler.start();
		clock.set(time("2026-10-01T03:00:00-04:00[America/New_York]")); timer.runNext();
		assertEquals(1, handoffs.get(), "resume within the window performs normal evaluation");

		timer = new FakeTimer(); handoffs.set(0); clock.set(time("2026-10-01T01:00:00-04:00[America/New_York]"));
		scheduler = scheduler(clock, timer, handoffs, enabled()); scheduler.start();
		clock.set(time("2026-10-01T09:00:00-04:00[America/New_York]")); timer.runNext();
		assertEquals(0, handoffs.get(), "a timer delayed past 04:00 must not update at 09:00");
		assertTrue(timer.peek().delay().compareTo(Duration.ofHours(16)) >= 0);
	}

	@Test void preferenceIsRereadAndFourDeferralsProduceNoFifthEvaluation() {
		var timer = new FakeTimer(); var handoffs = new AtomicInteger(); var reads = new AtomicInteger();
		var clock = new AtomicReference<>(time("2026-10-01T02:00:00-04:00[America/New_York]"));
		var scheduler = scheduler(clock, timer, handoffs, () -> {
			reads.incrementAndGet(); return new WorkstationUpdatePreference(WorkstationUpdatePreference.Status.DISABLED);
		}); scheduler.start();
		for (int i = 0; i < 4; i++) timer.runNext();
		assertEquals(4, reads.get()); assertEquals(0, handoffs.get());
		assertTrue(timer.peek().delay().compareTo(Duration.ofHours(22)) >= 0, "fifth evaluation waits for next local window");
	}

	@Test void oneRealHandoffSuppressesSameWindowHandoffsAndNextWindowResets() {
		var timer = new FakeTimer(); var handoffs = new AtomicInteger();
		var clock = new AtomicReference<>(time("2026-10-01T02:00:00-04:00[America/New_York]"));
		var scheduler = scheduler(clock, timer, handoffs, enabled()); scheduler.start(); timer.runNext();
		assertEquals(1, handoffs.get());
		clock.set(time("2026-10-02T02:00:00-04:00[America/New_York]")); timer.runNext();
		assertEquals(2, handoffs.get(), "the local-date window resets the one-handoff budget");
	}

	private static InSessionAutomaticUpdateScheduler scheduler(AtomicReference<ZonedDateTime> clock,
			FakeTimer timer, AtomicInteger handoffs, WorkstationUpdatePreferenceProvider preferences) {
		return new InSessionAutomaticUpdateScheduler(clock::get, new UnattendedUpdateEvaluationService(preferences),
				() -> inputs(clock.get()), ignored -> handoffs.incrementAndGet(), new UnattendedUpdateRetryPolicy(), timer);
	}
	private static WorkstationUpdatePreferenceProvider enabled() { return () -> new WorkstationUpdatePreference(WorkstationUpdatePreference.Status.ENABLED); }
	private static UnattendedUpdateEligibility.Inputs inputs(ZonedDateTime now) {
		return new UnattendedUpdateEligibility.Inputs(new WorkstationUpdatePreference(WorkstationUpdatePreference.Status.ENABLED),
				ApplicationUpdatePolicyState.RECOMMENDED, new UnattendedUpdateEligibility.Availability(true,
				SemanticVersion.parse("1.0.129"), SemanticVersion.parse("1.0.130"), SemanticVersion.parse("1.0.130"),
				ReleaseChannel.PRODUCTION, ReleaseChannel.PRODUCTION, true, true), now,
				Optional.of(now.toInstant().minusSeconds(1800)), false, false, true, true, true);
	}
	private static ZonedDateTime time(String value) { return ZonedDateTime.parse(value); }
	private record Task(Runnable task, Duration delay) {}
	private static final class FakeTimer implements InSessionAutomaticUpdateScheduler.Timer {
		private final ArrayDeque<Task> tasks = new ArrayDeque<>();
		@Override public void schedule(Runnable task, Duration delay) { tasks.clear(); tasks.add(new Task(task, delay)); }
		int size() { return tasks.size(); } Task peek() { return tasks.element(); } Task remove() { return tasks.remove(); }
		void runNext() { remove().task().run(); }
		@Override public void stop() { tasks.clear(); }
		@Override public void close() { tasks.clear(); }
	}
}
