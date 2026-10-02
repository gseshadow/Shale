package com.shale.core.update;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Owns the one process-local Phase 13C timer for an authenticated desktop generation.
 * It never wakes the workstation and delegates every gate to the Phase 13B evaluator.
 */
public final class InSessionAutomaticUpdateScheduler implements AutoCloseable {
	interface Timer extends AutoCloseable {
		void schedule(Runnable task, Duration delay);
		void stop();
		@Override void close();
	}

	private final Supplier<ZonedDateTime> now;
	private final UnattendedUpdateEvaluationService evaluator;
	private final Supplier<UnattendedUpdateEligibility.Inputs> inputs;
	private final Consumer<UnattendedUpdateEligibility.Inputs> handoff;
	private final UnattendedUpdateRetryPolicy retries;
	private final Timer timer;
	private final AtomicLong generation = new AtomicLong();
	private UnattendedUpdateRetryPolicy.State state;
	private boolean running;

	public InSessionAutomaticUpdateScheduler(WorkstationUpdatePreferenceProvider preferences,
			Supplier<UnattendedUpdateEligibility.Inputs> inputs,
			Consumer<UnattendedUpdateEligibility.Inputs> handoff) {
		this(ZonedDateTime::now, new UnattendedUpdateEvaluationService(preferences), inputs, handoff,
				new UnattendedUpdateRetryPolicy(), new ExecutorTimer());
	}

	InSessionAutomaticUpdateScheduler(Supplier<ZonedDateTime> now,
			UnattendedUpdateEvaluationService evaluator,
			Supplier<UnattendedUpdateEligibility.Inputs> inputs,
			Consumer<UnattendedUpdateEligibility.Inputs> handoff,
			UnattendedUpdateRetryPolicy retries, Timer timer) {
		this.now = Objects.requireNonNull(now); this.evaluator = Objects.requireNonNull(evaluator);
		this.inputs = Objects.requireNonNull(inputs); this.handoff = Objects.requireNonNull(handoff);
		this.retries = Objects.requireNonNull(retries); this.timer = Objects.requireNonNull(timer);
	}

	public synchronized void start() {
		if (running) return;
		running = true;
		long token = generation.incrementAndGet();
		System.getLogger(getClass().getName()).log(System.Logger.Level.INFO, "Automatic update scheduler started");
		schedule(token, delayUntilCandidate(now.get()));
	}

	public synchronized void stop() {
		if (!running) return;
		running = false;
		generation.incrementAndGet();
		timer.stop();
		System.getLogger(getClass().getName()).log(System.Logger.Level.INFO, "Automatic update scheduler stopped");
	}

	public synchronized boolean isRunning() { return running; }

	private void run(long token) {
		ZonedDateTime observed = now.get();
		synchronized (this) {
			if (!running || token != generation.get()) return;
			if (!UnattendedUpdateEligibility.insideWindow(observed)) {
				schedule(token, delayUntilCandidate(observed));
				return;
			}
			if (state == null || !state.window().equals(UnattendedUpdateRetryPolicy.WindowId.at(observed)))
				state = UnattendedUpdateRetryPolicy.State.empty(observed);
			if (!retries.mayEvaluate(state)) {
				schedule(token, delayUntilNextWindow(observed));
				return;
			}
			state = retries.forEvaluation(state, observed);
		}

		try {
			var decision = evaluator.evaluateAndHandoff(inputs, value -> {
				synchronized (InSessionAutomaticUpdateScheduler.this) {
					if (!running || token != generation.get() || !retries.mayHandoff(state)) return;
					state = retries.forHandoff(state);
				}
				System.getLogger(getClass().getName()).log(System.Logger.Level.INFO, "Automatic updater handoff initiated");
				handoff.accept(value);
			});
			System.getLogger(getClass().getName()).log(System.Logger.Level.INFO,
					"Automatic update evaluation: {0}", decision.name());
		} catch (RuntimeException failure) {
			System.getLogger(getClass().getName()).log(System.Logger.Level.WARNING,
					"Automatic update evaluation error: {0}", failure.getClass().getSimpleName());
		}
		synchronized (this) {
			if (!running || token != generation.get()) return;
			ZonedDateTime current = now.get();
			Duration delay = retries.mayEvaluate(state) && UnattendedUpdateEligibility.insideWindow(current)
					? UnattendedUpdateRetryPolicy.RETRY_INTERVAL : delayUntilNextWindow(current);
			schedule(token, delay);
		}
	}

	private void schedule(long token, Duration delay) {
		timer.schedule(() -> run(token), delay.isNegative() ? Duration.ZERO : delay);
	}

	static Duration delayUntilCandidate(ZonedDateTime time) {
		if (UnattendedUpdateEligibility.insideWindow(time)) return Duration.ZERO;
		return delayUntilNextWindow(time);
	}

	static Duration delayUntilNextWindow(ZonedDateTime time) {
		var date = time.toLocalDate();
		if (!time.toLocalTime().isBefore(UnattendedUpdateEligibility.WINDOW_START)) date = date.plusDays(1);
		ZonedDateTime candidate = date.atTime(UnattendedUpdateEligibility.WINDOW_START).atZone(time.getZone());
		return Duration.between(time.toInstant(), candidate.toInstant());
	}

	@Override public synchronized void close() { stop(); timer.close(); }

	private static final class ExecutorTimer implements Timer {
		private ScheduledExecutorService executor;
		private ScheduledFuture<?> pending;
		@Override public synchronized void schedule(Runnable task, Duration delay) {
			if (pending != null) pending.cancel(false);
			if (executor == null || executor.isShutdown()) executor = Executors.newSingleThreadScheduledExecutor(r -> {
				Thread thread = new Thread(r, "automatic-update-scheduler"); thread.setDaemon(true); return thread;
			});
			pending = executor.schedule(task, Math.max(0, delay.toMillis()), TimeUnit.MILLISECONDS);
		}
		@Override public synchronized void stop() {
			if (pending != null) pending.cancel(false); pending = null;
			if (executor != null) executor.shutdownNow(); executor = null;
		}
		@Override public synchronized void close() { stop(); }
	}
}
