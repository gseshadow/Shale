package com.shale.ui.services;

import java.util.Map;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface UiRuntimeBridge {

	record UserSessionView(UUID sessionId, String clientType, Instant issuedAt, Instant expiresAt,
			Instant lastRefreshedAt, Instant revokedAt, boolean currentSession) {}
	record AdminSessionView(UUID sessionId, int userId, String userDisplayName, String userEmail,
			String clientType, Instant issuedAt, Instant expiresAt, Instant lastRefreshedAt,
			Instant revokedAt, String revocationReason, boolean currentSession) {}
	record AdminSessionFilter(Integer userId, String clientType, boolean activeOnly, Instant since, int page, int size) {}
	record AdminSessionPage(List<AdminSessionView> items, int page, int size) {}

	interface UserSessionManagement {
		List<UserSessionView> list();
		void revoke(UUID sessionId);
		void revokeOthers();
	}

	/** Empty is the expected JDBC-only/older-server compatibility state. */
	default Optional<UserSessionManagement> userSessionManagement() { return Optional.empty(); }

	interface AdminSessionManagement {
		AdminSessionPage list(AdminSessionFilter filter);
		void revoke(UUID sessionId);
	}
	/** Empty unless the authenticated desktop has an enrolled server session. Server authorization remains authoritative. */
	default Optional<AdminSessionManagement> adminSessionManagement() { return Optional.empty(); }

	void onLoginSuccess(int userId, int shaleClientId, String email);

	void onLogout();

	/** Best-effort process shutdown hook; implementations must not make shutdown depend on telemetry. */
	default void onShutdown() { onLogout(); }

	/** Starts heartbeat only for the bridge's successfully enrolled current instance. */
	default void startApplicationInstanceHeartbeat(Supplier<Optional<Instant>> lastHumanActivityAt) {}

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

	default void publishOrganizationUpdated(int organizationId, int shaleClientId, int updatedByUserId) {
		publishEntityUpdated("Organization", organizationId, shaleClientId, updatedByUserId, null);
	}

	default void publishCaseLinkChanged(long caseId, Long caseLinkId, Long externalLinkId, Integer linkTypeId,
			int shaleClientId, int updatedByUserId, String change) {
		publishEntityUpdated(LiveUpdateEvents.ENTITY_CASE_LINK, caseLinkId == null ? caseId : caseLinkId, shaleClientId, updatedByUserId,
				LiveUpdateEvents.caseLinkPatch(caseId, caseLinkId, externalLinkId, linkTypeId, change));
	}

	default void publishCaseLinkShareChanged(long caseId, long caseLinkId, Long shareId, Integer contactId,
			int shaleClientId, int updatedByUserId, String change) {
		publishEntityUpdated(LiveUpdateEvents.ENTITY_CASE_LINK_SHARE, shareId == null ? caseLinkId : shareId, shaleClientId, updatedByUserId,
				LiveUpdateEvents.caseLinkSharePatch(caseId, caseLinkId, shareId, contactId, change));
	}

	default void publishLinkTypeChanged(int linkTypeId, int shaleClientId, int updatedByUserId, String change) {
		publishEntityUpdated(LiveUpdateEvents.ENTITY_LINK_TYPE, linkTypeId, shaleClientId, updatedByUserId,
				LiveUpdateEvents.linkTypePatch(linkTypeId, change));
	}

	default void publishEntityAuditActivityAdded(Long entityActionAuditLogId, int shaleClientId, int updatedByUserId) {
		publishEntityUpdated(LiveUpdateEvents.ENTITY_AUDIT_ACTIVITY, entityActionAuditLogId == null ? 0L : entityActionAuditLogId, shaleClientId, updatedByUserId,
				LiveUpdateEvents.auditActivityPatch(entityActionAuditLogId));
	}

	default void publishCaseDatesChanged(long caseId, int shaleClientId, int updatedByUserId, String change) {
		publishEntityUpdated(LiveUpdateEvents.ENTITY_CASE_DATES, caseId, shaleClientId, updatedByUserId,
				LiveUpdateEvents.caseDatesPatch(caseId, change));
	}

	default void publishCaseDateTypeChanged(int typeId, int shaleClientId, int updatedByUserId) {
		publishEntityUpdated(LiveUpdateEvents.ENTITY_CASE_DATE_TYPES, typeId, shaleClientId, updatedByUserId, null);
	}

	default void publishCaseDatePresentationChanged(long configurationId, String purpose,
			int shaleClientId, int updatedByUserId) {
		publishEntityUpdated(LiveUpdateEvents.ENTITY_CASE_DATE_PRESENTATION, configurationId, shaleClientId,
				updatedByUserId, LiveUpdateEvents.caseDatePresentationPatch(purpose));
	}

	// --- Generic subscriptions (recommended)
	default void subscribeEntityUpdated(Consumer<EntityUpdatedEvent> handler) {
	}

	default void unsubscribeEntityUpdated(Consumer<EntityUpdatedEvent> handler) {
	}

	default void subscribeConnectivity(Consumer<ConnectivityEvent> handler) {
	}

	default void unsubscribeConnectivity(Consumer<ConnectivityEvent> handler) {
	}

	/**
	 * Performs a fresh, best-effort connectivity verification using runtime infrastructure.
	 * Optional.empty() means the runtime does not support an active recheck.
	 */
	default Optional<Boolean> recheckConnectivity() {
		return Optional.empty();
	}

	// --- Case-specific subscriptions (kept for now)
	default void subscribeCaseUpdated(Consumer<CaseUpdatedEvent> handler) {
	}

	default void unsubscribeCaseUpdated(Consumer<CaseUpdatedEvent> handler) {
	}

	default String getClientInstanceId() {
		return "";
	}

	default boolean openPath(Path path) {
		return false;
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
			Map<String, Object> patch,
			String clientInstanceId
	) {
	}

	record ConnectivityEvent(
			boolean online,
			String detail
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
