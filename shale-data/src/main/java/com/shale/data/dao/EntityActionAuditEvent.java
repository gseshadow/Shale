package com.shale.data.dao;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public record EntityActionAuditEvent(
		long auditEventId,
		int shaleClientId,
		int actorUserId,
		EntityType entityType,
		long entityId,
		Action action,
		Instant occurredAtUtc,
		EntityType parentEntityType,
		Long parentEntityId,
		String correlationId,
		String source,
		Map<MetadataKey, String> metadata) {

	public enum EntityType { LINK_TYPE, CASE_LINK, CASE_LINK_SHARE }

	public enum Action {
		CREATED,
		OVERRIDE_CREATED,
		UPDATED,
		ACTIVATED,
		DEACTIVATED,
		OVERRIDE_RESET,
		DELETED,
		PRIMARY_SET,
		REORDERED,
		ADDED,
		REMOVED
	}

	public enum MetadataKey {
		CASE_ID,
		CASE_LINK_ID,
		CASE_LINK_SHARE_ID,
		EXTERNAL_LINK_ID,
		LINK_TYPE_ID,
		CONTACT_ID,
		PREVIOUS_PRIMARY_CASE_LINK_ID,
		NEW_PRIMARY_CASE_LINK_ID,
		REORDERED_LINK_COUNT,
		ACTIVE
	}

	private static final Set<String> PROHIBITED_KEY_FRAGMENTS = Set.of(
			"url", "description", "note", "name", "email", "phone", "credential", "rowver", "dto", "command", "sql", "exception");

	public EntityActionAuditEvent {
		if (shaleClientId <= 0) throw new IllegalArgumentException("shaleClientId must be > 0");
		if (actorUserId <= 0) throw new IllegalArgumentException("actorUserId must be > 0");
		Objects.requireNonNull(entityType, "entityType");
		if (entityId <= 0) throw new IllegalArgumentException("entityId must be > 0");
		Objects.requireNonNull(action, "action");
		occurredAtUtc = occurredAtUtc == null ? Instant.now() : occurredAtUtc;
		if (parentEntityId != null && parentEntityId <= 0) throw new IllegalArgumentException("parentEntityId must be > 0 when present");
		if (parentEntityId != null) Objects.requireNonNull(parentEntityType, "parentEntityType");
		metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
	}

	public static EntityActionAuditEvent now(int tenant, int actor, EntityType entityType, long entityId, Action action,
			EntityType parentType, Long parentId, Map<MetadataKey, ?> metadata) {
		return new EntityActionAuditEvent(0, tenant, actor, entityType, entityId, action, Instant.now(), parentType, parentId, null, "SHALE_DESKTOP", stringifyMetadata(metadata));
	}

	public static Map<MetadataKey, String> stringifyMetadata(Map<MetadataKey, ?> source) {
		if (source == null || source.isEmpty()) return Map.of();
		java.util.EnumMap<MetadataKey, String> out = new java.util.EnumMap<>(MetadataKey.class);
		for (Map.Entry<MetadataKey, ?> entry : source.entrySet()) {
			MetadataKey key = Objects.requireNonNull(entry.getKey(), "metadata key");
			String keyText = key.name().toLowerCase(java.util.Locale.ROOT);
			for (String prohibited : PROHIBITED_KEY_FRAGMENTS) {
				if (keyText.contains(prohibited)) throw new IllegalArgumentException("metadata key is not safe for audit");
			}
			Object value = entry.getValue();
			if (value == null) continue;
			if (!(value instanceof Number || value instanceof Boolean || value instanceof String)) {
				throw new IllegalArgumentException("metadata value must be a safe scalar");
			}
			String text = value.toString();
			if (text.length() > 128) throw new IllegalArgumentException("metadata value is too long");
			out.put(key, text);
		}
		return Map.copyOf(out);
	}
}
