package com.shale.data.dao;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.shale.core.service.ContactServicePort.*;

class ContactMutationContractTest {
    private static String source() throws Exception { return Files.readString(Path.of("src/main/java/com/shale/data/dao/ContactMutationDao.java")); }

    @Test void commandsDefensivelyCopyOptimisticConcurrencyTokens() {
        byte[] token={1,2}; var c=new AssignmentLifecycleCommand(DefinitionCategory.SPECIALTY,7,9,11,13,token); token[0]=8;
        assertArrayEquals(new byte[]{1,2},c.expectedRowVer()); byte[] returned=c.expectedRowVer();returned[1]=8;assertArrayEquals(new byte[]{1,2},c.expectedRowVer());
    }
    @Test void mutationBoundaryContainsTenantActorAdminAndRollbackGuards() throws Exception {
        String s=source(); assertAll(()->assertTrue(s.contains("SESSION_CONTEXT(N'ShaleClientId')")),()->assertTrue(s.contains("ISNULL(IsRemoved,0)=0")),()->assertTrue(s.contains("ISNULL(is_admin,0)=1")),()->assertTrue(s.contains("con.rollback()")),()->assertTrue(s.contains("ExpectedRowVer is required")));
    }
    @Test void assignmentsValidateEffectiveLifecycleDuplicatesAndSameRowRestore() throws Exception {
        String s=source(); assertAll(()->assertTrue(s.contains("Global definition is shadowed")),()->assertTrue(s.contains("Definition is inactive or removed")),()->assertTrue(s.contains("activeAssignmentExists")),()->assertTrue(s.contains("SET IsDeleted=?")),()->assertTrue(s.contains("RowVer=?")));
    }
    @Test void credentialReorderRequiresCompleteSetAndWritesContiguousOrder() throws Exception {
        String s=source(); assertAll(()->assertTrue(s.contains("active.keySet().equals(expected.keySet())")),()->assertTrue(s.contains("Duplicate credential assignment ID")),()->assertTrue(s.contains("DisplayOrder=?")),()->assertTrue(s.contains("ORDERING_COUNT")));
    }
    @Test void expertBridgeUsesSystemKeyAndRemainsLegacyWriteAuthority() throws Exception {
        String s=source(); assertAll(()->assertTrue(s.contains("\"expert\".equals(d.key())")),()->assertTrue(s.contains("SET IsExpert=?")),()->assertTrue(s.contains("d.SystemKey=N'expert'")),()->assertFalse(s.contains("PartyRoles")),()->assertFalse(s.contains("CaseParties")),()->assertFalse(s.contains("CaseContacts")));
    }
    @Test void auditVocabularyAcceptsOnlyPhase1cCombinationsAndSafeMetadata() {
        assertDoesNotThrow(() -> EntityActionAuditEvent.now(7, 9,
                EntityActionAuditEvent.EntityType.CONTACT_CREDENTIAL, 11,
                EntityActionAuditEvent.Action.REORDERED, null, null,
                Map.of(EntityActionAuditEvent.MetadataKey.ORDERING_COUNT, 2)));
        assertThrows(IllegalArgumentException.class, () -> EntityActionAuditEvent.now(7, 9,
                EntityActionAuditEvent.EntityType.CONTACT_TYPE, 11,
                EntityActionAuditEvent.Action.REORDERED, null, null, Map.of()));
    }

    @Test void auditMetadataValidationHasDeliberateFailures() {
        assertDoesNotThrow(() -> auditEvent(null));
        var nullKey = new java.util.HashMap<EntityActionAuditEvent.MetadataKey, String>();
        nullKey.put(null, "unsafe");
        assertThrows(IllegalArgumentException.class, () -> auditEvent(nullKey));
        var nullValue = new java.util.EnumMap<EntityActionAuditEvent.MetadataKey, String>(EntityActionAuditEvent.MetadataKey.class);
        nullValue.put(EntityActionAuditEvent.MetadataKey.CONTACT_ID, null);
        assertThrows(IllegalArgumentException.class, () -> auditEvent(nullValue));
        assertThrows(IllegalArgumentException.class, () -> EntityActionAuditEvent.stringifyMetadata(
                Map.of(EntityActionAuditEvent.MetadataKey.CONTACT_ID, new Object())));
        assertThrows(IllegalArgumentException.class, () -> EntityActionAuditEvent.stringifyMetadata(
                Map.of(EntityActionAuditEvent.MetadataKey.CONTACT_ID, "x".repeat(129))));
        assertThrows(IllegalArgumentException.class, () -> new EntityActionAuditEvent(0, 7, 9,
                EntityActionAuditEvent.EntityType.CONTACT_TYPE, 11, EntityActionAuditEvent.Action.CREATED,
                null, EntityActionAuditEvent.EntityType.CONTACT_TYPE, null, null, "test", Map.of()));
        assertTrue(java.util.Arrays.stream(EntityActionAuditEvent.MetadataKey.values())
                .map(Enum::name).noneMatch(k -> k.matches(".*(NAME|EMAIL|PHONE|DESCRIPTION|CREDENTIAL|ROWVER|NOTE).*")),
                "the closed metadata vocabulary must remain PHI-safe");
    }

    private static EntityActionAuditEvent auditEvent(Map<EntityActionAuditEvent.MetadataKey, String> metadata) {
        return new EntityActionAuditEvent(0, 7, 9, EntityActionAuditEvent.EntityType.CONTACT_TYPE, 11,
                EntityActionAuditEvent.Action.CREATED, null, null, null, null, "test", metadata);
    }
}
