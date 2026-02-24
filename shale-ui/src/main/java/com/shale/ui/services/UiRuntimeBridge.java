package com.shale.ui.services;

import java.util.function.Consumer;

public interface UiRuntimeBridge {
	/**
	 * Desktop must: - initialize runtime session (RLS / tenant context) - enable DB access
	 * for DbSessionProvider
	 */
	void onLoginSuccess(int userId, int shaleClientId, String email);

	void onLogout();

	default void publishCaseUpdated(int caseId, int shaleClientId, int updatedByUserId) {
		// Optional runtime capability. Desktop implementation provides this.
	}

	default void subscribeCaseUpdated(Consumer<CaseUpdatedEvent> handler) {
		// Optional runtime capability. Desktop implementation provides this.
	}

	default void unsubscribeCaseUpdated(Consumer<CaseUpdatedEvent> handler) {
		// Optional runtime capability. Desktop implementation provides this.
	}

	record CaseUpdatedEvent(int caseId, int shaleClientId, int updatedByUserId) {
	}
}
