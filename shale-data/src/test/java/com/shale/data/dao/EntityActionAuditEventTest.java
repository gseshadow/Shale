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
	@Test
	void allowsCaseMaterialsEntitiesActionsAndSafeMetadata() {
		var metadata = new java.util.EnumMap<EntityActionAuditEvent.MetadataKey, Object>(EntityActionAuditEvent.MetadataKey.class);
		metadata.put(EntityActionAuditEvent.MetadataKey.CASE_ID, 42L);
		metadata.put(EntityActionAuditEvent.MetadataKey.MATERIAL_REQUEST_ID, 77L);
		metadata.put(EntityActionAuditEvent.MetadataKey.REQUEST_STATUS, "REQUESTED");
		EntityActionAuditEvent event = EntityActionAuditEvent.now(7, 9, EntityActionAuditEvent.EntityType.MATERIAL_REQUEST, 77, EntityActionAuditEvent.Action.STATUS_CHANGED, null, null, metadata);
		assertEquals("77", event.metadata().get(EntityActionAuditEvent.MetadataKey.MATERIAL_REQUEST_ID));
		assertEquals("REQUESTED", event.metadata().get(EntityActionAuditEvent.MetadataKey.REQUEST_STATUS));

		assertNotNull(EntityActionAuditEvent.EntityType.valueOf("MATERIAL_TYPE"));
		assertNotNull(EntityActionAuditEvent.EntityType.valueOf("MATERIAL_REQUEST_FOLLOW_UP"));
		assertNotNull(EntityActionAuditEvent.EntityType.valueOf("MATERIAL_ITEM"));
		assertNotNull(EntityActionAuditEvent.Action.valueOf("FOLLOW_UP_ADDED"));
		assertNotNull(EntityActionAuditEvent.Action.valueOf("LOCATION_UPDATED"));
		assertNotNull(EntityActionAuditEvent.Action.valueOf("RELEASED"));
		assertThrows(IllegalArgumentException.class, () -> EntityActionAuditEvent.now(7, 9, EntityActionAuditEvent.EntityType.MATERIAL_ITEM, 88, EntityActionAuditEvent.Action.PRIMARY_SET, null, null, Map.of()));
	}

	@Test
	void userAdministrationAuditUsesOnlySafeIdentifiersAndRoleState() {
		EntityActionAuditEvent updated=EntityActionAuditEvent.now(7,9,EntityActionAuditEvent.EntityType.USER,11,EntityActionAuditEvent.Action.UPDATED,null,null,Map.of(EntityActionAuditEvent.MetadataKey.TARGET_USER_ID,11,EntityActionAuditEvent.MetadataKey.ADMIN_ROLE,false,EntityActionAuditEvent.MetadataKey.ATTORNEY_ROLE,true));
		assertEquals(Map.of(EntityActionAuditEvent.MetadataKey.TARGET_USER_ID,"11",EntityActionAuditEvent.MetadataKey.ADMIN_ROLE,"false",EntityActionAuditEvent.MetadataKey.ATTORNEY_ROLE,"true"),updated.metadata());
		EntityActionAuditEvent removed=EntityActionAuditEvent.now(7,9,EntityActionAuditEvent.EntityType.USER,11,EntityActionAuditEvent.Action.REMOVED,null,null,Map.of(EntityActionAuditEvent.MetadataKey.TARGET_USER_ID,11,EntityActionAuditEvent.MetadataKey.ACTIVE,false));
		String json=EntityActionAuditDao.metadataJson(removed.metadata());
		assertFalse(json.toLowerCase().matches(".*(email|phone|password|credential|rowver).*"));
	}

	@Test
	void formConfigurationReplacementUsesSafeAuthoritativeVocabulary() {
		var metadata = new java.util.EnumMap<EntityActionAuditEvent.MetadataKey, Object>(EntityActionAuditEvent.MetadataKey.class);
		metadata.put(EntityActionAuditEvent.MetadataKey.FORM_CONFIGURATION_ID, 41L);
		metadata.put(EntityActionAuditEvent.MetadataKey.FORM_KEY, "NEW_INTAKE");
		metadata.put(EntityActionAuditEvent.MetadataKey.SECTION_COUNT, 2);
		metadata.put(EntityActionAuditEvent.MetadataKey.CONFIGURED_FIELD_COUNT, 5);
		metadata.put(EntityActionAuditEvent.MetadataKey.INITIAL_CREATION, false);
		EntityActionAuditEvent event = EntityActionAuditEvent.now(7, 9, EntityActionAuditEvent.EntityType.FORM_CONFIGURATION, 41, EntityActionAuditEvent.Action.UPDATED, null, null, metadata);
		assertEquals(7, event.shaleClientId());
		assertEquals(9, event.actorUserId());
		assertEquals(41, event.entityId());
		assertEquals("NEW_INTAKE", event.metadata().get(EntityActionAuditEvent.MetadataKey.FORM_KEY));
		assertEquals("5", event.metadata().get(EntityActionAuditEvent.MetadataKey.CONFIGURED_FIELD_COUNT));
		assertThrows(IllegalArgumentException.class, () -> EntityActionAuditEvent.now(7, 9, EntityActionAuditEvent.EntityType.FORM_CONFIGURATION, 41, EntityActionAuditEvent.Action.DELETED, null, null, Map.of()));
	}

}
