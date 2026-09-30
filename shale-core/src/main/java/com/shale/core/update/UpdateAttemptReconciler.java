package com.shale.core.update;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;

import com.shale.core.model.SemanticVersion;

public final class UpdateAttemptReconciler {
	private final UpdateAttemptStore store;
	private final Clock clock;

	public UpdateAttemptReconciler(UpdateAttemptStore store, Clock clock) { this.store=store; this.clock=clock; }

	public void reconcile(String runningVersion) throws IOException {
		SemanticVersion actual;
		try { actual=SemanticVersion.parse(runningVersion); } catch (RuntimeException ex) { return; }
		for (UpdateAttempt attempt : store.readAll()) {
			if (attempt.state().terminal()) {
				if (Duration.between(attempt.lastUpdatedAt(), clock.instant()).compareTo(UpdateAttemptStore.RETENTION)>0) store.delete(attempt.attemptId());
				continue;
			}
			if (attempt.state()==UpdateAttemptState.INSTALL_APPLIED && attempt.targetVersion()!=null) {
				try {
					if (actual.compareTo(SemanticVersion.parse(attempt.targetVersion()))>=0) {
						store.transition(attempt.attemptId(),UpdateAttemptState.COMPLETED,null,null,runningVersion); continue;
					}
				} catch (RuntimeException ignored) { }
			}
			if (Duration.between(attempt.startedAt(),clock.instant()).compareTo(UpdateAttemptStore.RECONCILIATION_WINDOW)>0)
				store.transition(attempt.attemptId(),UpdateAttemptState.OUTCOME_UNKNOWN,null,null,runningVersion);
		}
	}
}
