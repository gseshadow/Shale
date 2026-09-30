package com.shale.core.update;

public enum UpdateAttemptState {
	STARTED(false),
	UPDATER_LAUNCHED(false),
	INSTALL_APPLIED(false),
	COMPLETED(true),
	FAILED(true),
	OUTCOME_UNKNOWN(true);

	private final boolean terminal;

	UpdateAttemptState(boolean terminal) {
		this.terminal = terminal;
	}

	public boolean terminal() {
		return terminal;
	}
}
