package com.shale.ui.services;

import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public final class UpdatePollingService {
	static final long DEFAULT_POLL_MINUTES = 15;

	private final UiUpdateLauncher updateLauncher;
	private final Consumer<UiUpdateLauncher.UpdateCheckResult> resultConsumer;
	private final long pollMinutes;
	private ScheduledExecutorService scheduler;

	public UpdatePollingService(
			UiUpdateLauncher updateLauncher,
			Consumer<UiUpdateLauncher.UpdateCheckResult> resultConsumer) {
		this(updateLauncher, resultConsumer, DEFAULT_POLL_MINUTES);
	}

	UpdatePollingService(
			UiUpdateLauncher updateLauncher,
			Consumer<UiUpdateLauncher.UpdateCheckResult> resultConsumer,
			long pollMinutes) {
		this.updateLauncher = Objects.requireNonNull(updateLauncher, "updateLauncher");
		this.resultConsumer = Objects.requireNonNull(resultConsumer, "resultConsumer");
		this.pollMinutes = pollMinutes;
	}

	public void start() {
		if (scheduler != null && !scheduler.isShutdown()) {
			System.out.println("[UpdatePoll] start skipped: poller already running");
			return;
		}
		scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
			Thread t = new Thread(r, "update-poll-worker");
			t.setDaemon(true);
			return t;
		});
		System.out.println("[UpdatePoll] poller started: cadence=" + pollMinutes + "m");
		scheduler.scheduleAtFixedRate(this::runSafely, pollMinutes, pollMinutes, TimeUnit.MINUTES);
	}

	public void stop() {
		if (scheduler != null) {
			scheduler.shutdownNow();
			scheduler = null;
			System.out.println("[UpdatePoll] poller stopped");
		}
	}

	private void runSafely() {
		try {
			UiUpdateLauncher.UpdateCheckResult result = updateLauncher.checkForUpdate();
			System.out.println("[UpdatePoll] check complete: updateAvailable=" + result.updateAvailable() + ", mandatory=" + result.mandatory());
			resultConsumer.accept(result);
		} catch (RuntimeException ex) {
			System.err.println("[UpdatePoll] check failed: " + ex.getMessage());
		}
	}
}
