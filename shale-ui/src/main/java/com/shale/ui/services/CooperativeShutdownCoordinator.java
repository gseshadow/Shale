package com.shale.ui.services;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.function.BooleanSupplier;

import com.shale.core.update.CooperativeShutdownReadiness;

/** Future-facing normal-lifecycle shutdown seam. It never closes a child window or terminates a process. */
public final class CooperativeShutdownCoordinator {
	private final Supplier<CooperativeShutdownReadiness> readiness;
	private final Consumer<Runnable> dispatcher;
	private final Runnable normalShutdown;

	public CooperativeShutdownCoordinator(Supplier<CooperativeShutdownReadiness> readiness,
			Consumer<Runnable> dispatcher, Runnable normalShutdown) {
		this.readiness = Objects.requireNonNull(readiness);
		this.dispatcher = Objects.requireNonNull(dispatcher);
		this.normalShutdown = Objects.requireNonNull(normalShutdown);
	}

	public CooperativeShutdownReadiness requestIfReady() {
		return requestIfReady(() -> true);
	}

	/** Rechecks on the UI dispatcher and shuts down only after the handoff action succeeds. */
	public CooperativeShutdownReadiness requestIfReady(BooleanSupplier handoff) {
		Objects.requireNonNull(handoff);
		CooperativeShutdownReadiness inspected = readiness.get();
		if (!inspected.permitsUnattendedShutdown()) return inspected;
		dispatcher.accept(() -> {
			if (readiness.get().permitsUnattendedShutdown() && handoff.getAsBoolean()) normalShutdown.run();
		});
		return inspected;
	}
}
