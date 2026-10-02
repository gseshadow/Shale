package com.shale.core.update;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Reads Phase 13A at evaluation time and crosses the handoff boundary only for an eligible decision. */
public final class UnattendedUpdateEvaluationService {
	private final WorkstationUpdatePreferenceProvider preferences;
	private final UnattendedUpdateEligibility eligibility;

	public UnattendedUpdateEvaluationService(WorkstationUpdatePreferenceProvider preferences) {
		this.preferences = Objects.requireNonNull(preferences, "preferences");
		this.eligibility = new UnattendedUpdateEligibility();
	}

	public UnattendedUpdateEligibility.Decision evaluateAndHandoff(
			Supplier<UnattendedUpdateEligibility.Inputs> currentInputs,
			Consumer<UnattendedUpdateEligibility.Inputs> handoff) {
		Objects.requireNonNull(currentInputs, "currentInputs");
		Objects.requireNonNull(handoff, "handoff");
		var observed = Objects.requireNonNull(currentInputs.get(), "currentInputs result");
		var input = new UnattendedUpdateEligibility.Inputs(preferences.current(), observed.policy(),
				observed.availability(), observed.workstationTime(), observed.lastForegroundActivity(),
				observed.foregroundVisible(), observed.activeWork(), observed.cooperativeShutdownAvailable(),
				observed.updateLockAvailable(), observed.windowsCapabilityAvailable());
		var decision = eligibility.resolve(input);
		if (decision == UnattendedUpdateEligibility.Decision.ELIGIBLE) {
			// Preference and every volatile safety signal are deliberately reread immediately
			// before crossing the real handoff boundary.
			var reobserved = Objects.requireNonNull(currentInputs.get(), "currentInputs recheck result");
			var rechecked = new UnattendedUpdateEligibility.Inputs(preferences.current(), reobserved.policy(),
					reobserved.availability(), reobserved.workstationTime(), reobserved.lastForegroundActivity(),
					reobserved.foregroundVisible(), reobserved.activeWork(), reobserved.cooperativeShutdownAvailable(),
					reobserved.updateLockAvailable(), reobserved.windowsCapabilityAvailable());
			decision = eligibility.resolve(rechecked);
			if (decision == UnattendedUpdateEligibility.Decision.ELIGIBLE) handoff.accept(rechecked);
		}
		return decision;
	}
}
