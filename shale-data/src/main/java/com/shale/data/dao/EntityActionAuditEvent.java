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

	public enum EntityType { CONTACT, CONTACT_PHONE_NUMBER, CONTACT_EMAIL_ADDRESS, CONTACT_ADDRESS, CASE, CASE_STATUS, LINK_TYPE, CASE_LINK, CASE_LINK_SHARE, CASE_DATE, CASE_DATE_TYPE, CALENDAR_EVENT, CASE_DATE_ROLE_MAPPING, FORM_CONFIGURATION, MATERIAL_TYPE, MATERIAL_REQUEST, MATERIAL_REQUEST_FOLLOW_UP, MATERIAL_ITEM, USER, CONTACT_TYPE, SPECIALTY, CREDENTIAL_DEFINITION, CONTACT_CONTACT_TYPE, CONTACT_SPECIALTY, CONTACT_CREDENTIAL }

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
		REMOVED,
		STATUS_CHANGED,
		FOLLOW_UP_ADDED,
		LINKED,
		UNLINKED,
		LOCATION_UPDATED,
		RELEASED,
		RESTORED
	}

	public enum MetadataKey {
		CASE_ID,
		CASE_LINK_ID,
		CASE_LINK_SHARE_ID,
		CASE_DATE_ID,
		CALENDAR_EVENT_ID,
		CALENDAR_EVENT_TYPE_ID,
		EXTERNAL_LINK_ID,
		LINK_TYPE_ID,
		CONTACT_ID,
		PREVIOUS_PRIMARY_CASE_LINK_ID,
		NEW_PRIMARY_CASE_LINK_ID,
		REORDERED_LINK_COUNT,
		ACTIVE,
		MATERIAL_TYPE_ID,
		MATERIAL_REQUEST_ID,
		MATERIAL_REQUEST_FOLLOW_UP_ID,
		MATERIAL_ITEM_ID,
		ORGANIZATION_ID,
		ASSIGNED_TO_USER_ID,
		REQUEST_STATUS,
		PREVIOUS_REQUEST_STATUS,
		ITEM_FORMAT,
		COMPLETENESS,
		CUSTODY_STATUS,
		QUANTITY_COUNT,
		PAGE_COUNT,
		FILE_COUNT,
		HAS_EXTERNAL_LINK,
		TARGET_USER_ID,
		ADMIN_ROLE,
		ATTORNEY_ROLE,
		FORM_CONFIGURATION_ID,
		FORM_KEY,
		SECTION_COUNT,
		CONFIGURED_FIELD_COUNT,
		INITIAL_CREATION,
		SEMANTIC_ROLE,
		CASE_DATE_TYPE_ID,
		DEFINITION_ID,
		ORDERING_COUNT,
		KIND,
		PRIMARY
	}

	private static final Set<String> PROHIBITED_KEY_FRAGMENTS = Set.of(
			"url", "description", "note", "name", "email", "phone", "credential", "rowver", "dto", "command", "sql", "exception");

	public EntityActionAuditEvent {
		if (shaleClientId <= 0) throw new IllegalArgumentException("shaleClientId must be > 0");
		if (actorUserId <= 0) throw new IllegalArgumentException("actorUserId must be > 0");
		Objects.requireNonNull(entityType, "entityType");
		if (entityId <= 0) throw new IllegalArgumentException("entityId must be > 0");
		Objects.requireNonNull(action, "action");
		if (!isAllowedCombination(entityType, action)) throw new IllegalArgumentException("entity/action combination is not allowed for audit");
		occurredAtUtc = occurredAtUtc == null ? Instant.now() : occurredAtUtc;
		if ((parentEntityType == null) != (parentEntityId == null))
			throw new IllegalArgumentException("parentEntityType and parentEntityId must be supplied together");
		if (parentEntityId != null && parentEntityId <= 0)
			throw new IllegalArgumentException("parentEntityId must be > 0 when present");
		metadata = validateMetadata(metadata);
	}

	private static Map<MetadataKey, String> validateMetadata(Map<MetadataKey, String> source) {
		if (source == null || source.isEmpty()) return Map.of();
		var copy = new java.util.EnumMap<MetadataKey, String>(MetadataKey.class);
		for (Map.Entry<MetadataKey, String> entry : source.entrySet()) {
			if (entry.getKey() == null) throw new IllegalArgumentException("metadata key must not be null");
			if (entry.getValue() == null) throw new IllegalArgumentException("metadata value must not be null");
			validateSafeKey(entry.getKey());
			if (entry.getValue().length() > 128) throw new IllegalArgumentException("metadata value is too long");
			copy.put(entry.getKey(), entry.getValue());
		}
		return Map.copyOf(copy);
	}

	private static void validateSafeKey(MetadataKey key) {
		String keyText = key.name().toLowerCase(java.util.Locale.ROOT);
		for (String prohibited : PROHIBITED_KEY_FRAGMENTS)
			if (keyText.contains(prohibited)) throw new IllegalArgumentException("metadata key is not safe for audit");
	}

	private static boolean isAllowedCombination(EntityType entityType, Action action) {
		return switch (entityType) {
			case CONTACT -> action == Action.UPDATED;
			case CONTACT_PHONE_NUMBER, CONTACT_EMAIL_ADDRESS, CONTACT_ADDRESS -> action == Action.CREATED || action == Action.UPDATED || action == Action.REMOVED || action == Action.RESTORED || action == Action.REORDERED;
			case CASE -> action == Action.DELETED || action == Action.RESTORED;
			case CASE_STATUS -> action == Action.DEACTIVATED || action == Action.RESTORED;
			case LINK_TYPE, MATERIAL_TYPE -> action == Action.CREATED || action == Action.OVERRIDE_CREATED || action == Action.UPDATED || action == Action.ACTIVATED || action == Action.DEACTIVATED || action == Action.OVERRIDE_RESET || action == Action.DELETED || action == Action.REMOVED;
			case CASE_LINK -> action == Action.CREATED || action == Action.UPDATED || action == Action.DELETED || action == Action.PRIMARY_SET || action == Action.REORDERED;
			case CASE_LINK_SHARE -> action == Action.ADDED || action == Action.UPDATED || action == Action.REMOVED;
			case CASE_DATE -> action == Action.CREATED || action == Action.UPDATED || action == Action.DELETED || action == Action.ACTIVATED || action == Action.RESTORED || action == Action.LINKED || action == Action.UNLINKED;
			case CASE_DATE_TYPE -> action == Action.CREATED || action == Action.UPDATED || action == Action.ACTIVATED || action == Action.DEACTIVATED || action == Action.DELETED || action == Action.RESTORED;
			case CALENDAR_EVENT -> action == Action.CREATED || action == Action.UPDATED || action == Action.DELETED || action == Action.RESTORED || action == Action.LINKED || action == Action.UNLINKED;
			case CASE_DATE_ROLE_MAPPING -> action == Action.OVERRIDE_CREATED || action == Action.UPDATED || action == Action.OVERRIDE_RESET;
			case FORM_CONFIGURATION -> action == Action.CREATED || action == Action.UPDATED;
			case MATERIAL_REQUEST -> action == Action.CREATED || action == Action.UPDATED || action == Action.STATUS_CHANGED || action == Action.FOLLOW_UP_ADDED || action == Action.LINKED || action == Action.DELETED;
			case MATERIAL_REQUEST_FOLLOW_UP -> action == Action.CREATED || action == Action.FOLLOW_UP_ADDED;
			case USER -> action == Action.CREATED || action == Action.UPDATED || action == Action.ACTIVATED || action == Action.DEACTIVATED || action == Action.REMOVED;
			case MATERIAL_ITEM -> action == Action.CREATED || action == Action.UPDATED || action == Action.LINKED || action == Action.UNLINKED || action == Action.LOCATION_UPDATED || action == Action.RELEASED || action == Action.DELETED;
			case CONTACT_TYPE, SPECIALTY, CREDENTIAL_DEFINITION -> action == Action.CREATED || action == Action.OVERRIDE_CREATED || action == Action.UPDATED || action == Action.ACTIVATED || action == Action.DEACTIVATED || action == Action.REMOVED || action == Action.RESTORED;
			case CONTACT_CONTACT_TYPE, CONTACT_SPECIALTY -> action == Action.ADDED || action == Action.REMOVED || action == Action.RESTORED;
			case CONTACT_CREDENTIAL -> action == Action.ADDED || action == Action.REMOVED || action == Action.RESTORED || action == Action.REORDERED;
		};
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
			validateSafeKey(key);
			Object value = entry.getValue();
			if (value == null) throw new IllegalArgumentException("metadata value must not be null");
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
