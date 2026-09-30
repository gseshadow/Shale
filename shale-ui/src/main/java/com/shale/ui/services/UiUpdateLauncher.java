package com.shale.ui.services;

import com.shale.core.model.ApplicationUpdatePolicyState;
import com.shale.core.update.UnattendedUpdateEligibility;
import com.shale.core.update.UpdateInvocationMode;

public interface UiUpdateLauncher {
	record UpdateCheckResult(boolean updateAvailable, boolean mandatory) {
	}

	UpdateCheckResult checkForUpdate();

	void launchUpdater();

	default UnattendedUpdateEligibility.Availability automaticAvailability(
			ApplicationUpdatePolicyState policyState, String policyTargetVersion) {
		return UnattendedUpdateEligibility.Availability.unavailable();
	}

	default boolean automaticWindowsCapabilityAvailable() { return false; }
	default boolean automaticExecutionLockAvailable() { return false; }
	default void launchUpdater(UpdateInvocationMode mode) {
		if (mode != UpdateInvocationMode.MANUAL) throw new UnsupportedOperationException("Unattended updates are unavailable");
		launchUpdater();
	}
}
