package com.shale.core.update;

import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Objects;

/** Bounded per-local-window evaluation policy; persistence and wake-up remain outside this pure contract. */
public final class UnattendedUpdateRetryPolicy {
	public static final int MAX_EVALUATIONS_PER_WINDOW = 4;
	public static final int MAX_HANDOFFS_PER_WINDOW = 1;
	public static final Duration RETRY_INTERVAL = Duration.ofMinutes(30);

	public record WindowId(java.time.LocalDate localDate, ZoneId zone) {
		public static WindowId at(ZonedDateTime time) { return new WindowId(time.toLocalDate(), time.getZone()); }
	}

	public record State(WindowId window, int evaluations, int handoffs) {
		public State {
			Objects.requireNonNull(window, "window");
			if (evaluations < 0 || handoffs < 0) throw new IllegalArgumentException("Retry counters must be nonnegative");
		}
		public static State empty(ZonedDateTime time) { return new State(WindowId.at(time), 0, 0); }
	}

	public State forEvaluation(State prior, ZonedDateTime now) {
		State current = prior == null || !prior.window().equals(WindowId.at(now)) ? State.empty(now) : prior;
		if (current.evaluations() >= MAX_EVALUATIONS_PER_WINDOW) return current;
		return new State(current.window(), current.evaluations() + 1, current.handoffs());
	}

	public State forHandoff(State state) {
		if (state.handoffs() >= MAX_HANDOFFS_PER_WINDOW) return state;
		return new State(state.window(), state.evaluations(), state.handoffs() + 1);
	}

	public boolean mayEvaluate(State state) { return state.evaluations() < MAX_EVALUATIONS_PER_WINDOW; }
	public boolean mayHandoff(State state) { return state.handoffs() < MAX_HANDOFFS_PER_WINDOW; }
}
