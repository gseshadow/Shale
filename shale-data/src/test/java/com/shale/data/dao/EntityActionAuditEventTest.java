package com.shale.data.dao;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.Test;

final class EntityActionAuditEventTest {
	@Test
	void validatesPositiveTenantActorAndEntityIds() {
		assertThrows(IllegalArgumentException.class, () -> EntityActionAuditEvent.now(0, 1, EntityActionAuditEvent.EntityType.CASE_LINK, 1, EntityActionAuditEvent.Action.CREATED, null, null, Map.of()));
		assertThrows(IllegalArgumentException.class, () -> EntityActionAuditEvent.now(1, 0, EntityActionAuditEvent.EntityType.CASE_LINK, 1, EntityActionAuditEvent.Action.CREATED, null, null, Map.of()));
		assertThrows(IllegalArgumentException.class, () -> EntityActionAuditEvent.now(1, 1, EntityActionAuditEvent.EntityType.CASE_LINK, 0, EntityActionAuditEvent.Action.CREATED, null, null, Map.of()));
	}

	@Test
	void supportsRequiredVocabulary() {
		assertNotNull(EntityActionAuditEvent.EntityType.valueOf("LINK_TYPE"));
		assertNotNull(EntityActionAuditEvent.EntityType.valueOf("CASE_LINK"));
		assertNotNull(EntityActionAuditEvent.EntityType.valueOf("CASE_LINK_SHARE"));
		assertNotNull(EntityActionAuditEvent.Action.valueOf("OVERRIDE_RESET"));
		assertNotNull(EntityActionAuditEvent.Action.valueOf("PRIMARY_SET"));
		assertNotNull(EntityActionAuditEvent.Action.valueOf("REORDERED"));
	}

	@Test
	void metadataIsAllowlistedAndDefensivelyCopied() {
		var metadata = new java.util.EnumMap<EntityActionAuditEvent.MetadataKey, Object>(EntityActionAuditEvent.MetadataKey.class);
		metadata.put(EntityActionAuditEvent.MetadataKey.CASE_ID, 42L);
		EntityActionAuditEvent event = EntityActionAuditEvent.now(7, 9, EntityActionAuditEvent.EntityType.CASE_LINK, 11, EntityActionAuditEvent.Action.UPDATED, null, null, metadata);
		metadata.put(EntityActionAuditEvent.MetadataKey.CONTACT_ID, 55);
		assertEquals(Map.of(EntityActionAuditEvent.MetadataKey.CASE_ID, "42"), event.metadata());
		assertThrows(UnsupportedOperationException.class, () -> event.metadata().put(EntityActionAuditEvent.MetadataKey.ACTIVE, "true"));
	}

	@Test
	void rejectsUnsafeMetadataValues() {
		assertThrows(IllegalArgumentException.class, () -> EntityActionAuditEvent.now(7, 9, EntityActionAuditEvent.EntityType.CASE_LINK, 11, EntityActionAuditEvent.Action.UPDATED, null, null, Map.of(EntityActionAuditEvent.MetadataKey.CASE_ID, new byte[] {1, 2})));
		assertThrows(IllegalArgumentException.class, () -> EntityActionAuditEvent.now(7, 9, EntityActionAuditEvent.EntityType.CASE_LINK, 11, EntityActionAuditEvent.Action.UPDATED, null, null, Map.of(EntityActionAuditEvent.MetadataKey.CASE_ID, "x".repeat(129))));
	}

	@Test
	void writerSerializesOnlyAllowlistedMetadataAndDoesNotExposeSensitiveKeys() {
		String json = EntityActionAuditDao.metadataJson(Map.of(EntityActionAuditEvent.MetadataKey.CASE_LINK_ID, "12"));
		assertEquals("{\"CASE_LINK_ID\":\"12\"}", json);
		assertFalse(json.toLowerCase().contains("url"));
		assertFalse(json.toLowerCase().contains("note"));
	}

	@Test
	void eventTimestampIsUtcInstant() {
		Instant now = Instant.now();
		EntityActionAuditEvent event = new EntityActionAuditEvent(0, 7, 9, EntityActionAuditEvent.EntityType.LINK_TYPE, 3, EntityActionAuditEvent.Action.CREATED, now, null, null, null, "TEST", Map.of());
		assertSame(now, event.occurredAtUtc());
	}
}
