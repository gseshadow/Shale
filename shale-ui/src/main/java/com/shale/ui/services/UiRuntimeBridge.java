package com.shale.ui.services;

import java.util.Map;
import java.util.function.Consumer;

public interface UiRuntimeBridge {

	void onLoginSuccess(int userId, int shaleClientId, String email);

	void onLogout();

	// --- Generic publish (desktop implementation overrides)
	default void publishEntityUpdated(String entityType, long entityId,
			int shaleClientId, int updatedByUserId,
			String patchJsonOrNull) {
		// Optional runtime capability. Desktop implementation provides this.
	}

	// --- Single-field convenience publish (no JSON at call sites)
	default void publishEntityFieldUpdated(String entityType, long entityId,
			int shaleClientId, int updatedByUserId,
			String field, Object newValueOrNull) {

		String patch = patchOne(field, newValueOrNull);
		publishEntityUpdated(entityType, entityId, shaleClientId, updatedByUserId, patch);
	}

	// --- Back-compat wrappers
	default void publishCaseUpdated(int caseId, int shaleClientId, int updatedByUserId) {
		publishEntityUpdated("Case", caseId, shaleClientId, updatedByUserId, null);
	}

	default void publishCaseNameUpdated(int caseId, int shaleClientId, int updatedByUserId, String newName) {
		publishEntityFieldUpdated("Case", caseId, shaleClientId, updatedByUserId, "name", newName);
	}

	// --- Generic subscriptions (recommended)
	default void subscribeEntityUpdated(Consumer<EntityUpdatedEvent> handler) {
	}

	default void unsubscribeEntityUpdated(Consumer<EntityUpdatedEvent> handler) {
	}

	// --- Case-specific subscriptions (kept for now)
	default void subscribeCaseUpdated(Consumer<CaseUpdatedEvent> handler) {
	}

	default void unsubscribeCaseUpdated(Consumer<CaseUpdatedEvent> handler) {
	}

	default String getClientInstanceId() {
		return "";
	}
	


	/**
	 * Generic event all controllers can use. patchRaw is the original JSON string (useful for
	 * logging/debug). patch is the parsed version (may be empty if absent or parse failed).
	 */
	record EntityUpdatedEvent(
			int schemaVersion,
			String eventId,
			String entityType,
			long entityId,
			int shaleClientId,
			int updatedByUserId,
			String timestamp,
			String patchRaw,
			Map<String, Object> patch
	) {
	}

	/**
	 * Back-compat event used by existing controllers. rawPatchJson is the patch object JSON
	 * string, not the whole event.
	 */
	record CaseUpdatedEvent(
			int caseId,
			int shaleClientId,
			int updatedByUserId,
			String newName,
			String rawPatchJson,
			String clientInstanceId
	) {
	}

	// --- tiny JSON helper (enough for string/number/bool/null)
	private static String patchOne(String field, Object value) {
		if (field == null || field.isBlank()) {
			throw new IllegalArgumentException("field");
		}
		if (value == null) {
			// If you prefer "no patch", return null instead of "{}"
			return "{}";
		}

		String key = escapeJson(field);

		if (value instanceof Number || value instanceof Boolean) {
			return "{\"" + key + "\":" + value + "}";
		}

		return "{\"" + key + "\":\"" + escapeJson(String.valueOf(value)) + "\"}";
	}

	private static String escapeJson(String s) {
		return s.replace("\\", "\\\\")
				.replace("\"", "\\\"")
				.replace("\r", "\\r")
				.replace("\n", "\\n")
				.replace("\t", "\\t");
	}
}