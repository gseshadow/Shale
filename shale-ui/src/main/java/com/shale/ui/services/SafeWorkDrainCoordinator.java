package com.shale.ui.services;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import com.shale.core.model.ApplicationUpdatePolicyState;
import com.shale.core.model.ApplicationVersionEnforcementState;

/**
 * Session-scoped entry gate and small active-work tracker. Registrations are leases: policy changes
 * never invalidate a lease which was granted while work was allowed.
 */
public final class SafeWorkDrainCoordinator {
	public static final String BLOCKED_MESSAGE = "Shale must be updated before starting new work.";
	private final Consumer<ApplicationVersionEnforcementState> listener;
	private ApplicationVersionEnforcementState state = ApplicationVersionEnforcementState.UNKNOWN_GRACE;
	private int activeWork;

	public SafeWorkDrainCoordinator(Consumer<ApplicationVersionEnforcementState> listener) {
		this.listener = Objects.requireNonNull(listener);
	}

	public synchronized ApplicationVersionEnforcementState state() { return state; }
	public synchronized int activeWorkCount() { return activeWork; }
	public synchronized boolean permitsNewWork() { return permits(state); }

	/** Returns a grandfathering lease, or {@code null} when the workflow must not open. */
	public synchronized Registration tryStart(String workflow) {
		Objects.requireNonNull(workflow);
		if (!permits(state)) return null;
		activeWork++;
		return new Registration(this, workflow);
	}

	/** Derives enforcement only from the authoritative/bounded Phase 11A result. */
	public void apply(ApplicationUpdatePolicyCoordinator.Presentation policy) {
		ApplicationVersionEnforcementState next;
		synchronized (this) {
			boolean prohibited = policy != null && policy.state() == ApplicationUpdatePolicyState.REQUIRED_DEADLINE_REACHED;
			if (prohibited) next = activeWork == 0
					? ApplicationVersionEnforcementState.BLOCKED_NEW_WORK
					: ApplicationVersionEnforcementState.DRAINING_REQUIRED_UPDATE;
			else if (policy == null || policy.state() == ApplicationUpdatePolicyState.UNKNOWN)
				next = ApplicationVersionEnforcementState.UNKNOWN_GRACE;
			else if (!permits(state)) next = ApplicationVersionEnforcementState.RECOVERING;
			else next = ApplicationVersionEnforcementState.ALLOWED;
			setState(next);
		}
		if (next == ApplicationVersionEnforcementState.RECOVERING) setAllowedAfterRecoverySignal();
	}

	public synchronized void reset() { activeWork = 0; setState(ApplicationVersionEnforcementState.UNKNOWN_GRACE); }

	private synchronized void finish() {
		if (activeWork > 0) activeWork--;
		if (activeWork == 0 && state == ApplicationVersionEnforcementState.DRAINING_REQUIRED_UPDATE)
			setState(ApplicationVersionEnforcementState.BLOCKED_NEW_WORK);
	}
	private synchronized void setAllowedAfterRecoverySignal() { setState(ApplicationVersionEnforcementState.ALLOWED); }
	private void setState(ApplicationVersionEnforcementState next) {
		if (state == next) return;
		state = next;
		listener.accept(next);
	}
	private static boolean permits(ApplicationVersionEnforcementState value) {
		return value == ApplicationVersionEnforcementState.ALLOWED
				|| value == ApplicationVersionEnforcementState.UNKNOWN_GRACE
				|| value == ApplicationVersionEnforcementState.RECOVERING;
	}

	public static final class Registration implements AutoCloseable {
		private final SafeWorkDrainCoordinator owner;
		private final String workflow;
		private final AtomicBoolean closed = new AtomicBoolean();
		private Registration(SafeWorkDrainCoordinator owner, String workflow) { this.owner=owner; this.workflow=workflow; }
		public String workflow() { return workflow; }
		@Override public void close() { if (closed.compareAndSet(false, true)) owner.finish(); }
	}
}
