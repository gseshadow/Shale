package com.shale.data.dao;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;

final class ContactPhase2BAuditMigrationContractTest {
    private static final Path MIGRATION=AuditEntityTypeMigrationChain.CURRENT;
    private static final Path VERIFY=Path.of("..","docs","sql","verification","2026-08-26_contacts_phase2b_audit_allowlist_verification.sql");

    @Test void contactUpdatedIsTheOnlyContactEntityAction() {
        var event=EntityActionAuditEvent.now(7,9,EntityActionAuditEvent.EntityType.CONTACT,41,
                EntityActionAuditEvent.Action.UPDATED,null,null,
                Map.of(EntityActionAuditEvent.MetadataKey.CONTACT_ID,41));
        assertEquals(Map.of(EntityActionAuditEvent.MetadataKey.CONTACT_ID,"41"),event.metadata());
        assertThrows(IllegalArgumentException.class,()->EntityActionAuditEvent.now(7,9,
                EntityActionAuditEvent.EntityType.CONTACT,41,EntityActionAuditEvent.Action.CREATED,null,null,Map.of()));
    }

    @Test void successorPreservesAllTokensAndAddsExactlyContact() throws Exception {
        String sql=Files.readString(MIGRATION);
        Set<String> expected=AuditEntityTypeMigrationChain.currentlyRequiredVocabulary();
        List<String> currentTokens=AuditEntityTypeMigrationChain.declaredAllowlistTokens(sql);
        assertEquals(expected,Set.copyOf(currentTokens));
        assertEquals(currentTokens.size(),Set.copyOf(currentTokens).size(),
                "the current authoritative allowlist must not contain duplicate tokens");
        assertEquals(1,currentTokens.stream().filter("CONTACT"::equals).count(),
                "the Phase 2B successor must add CONTACT exactly once");
        Set<String> predecessor=AuditEntityTypeMigrationChain.declaredAllowlist(
                AuditEntityTypeMigrationChain.read(AuditEntityTypeMigrationChain.PHASE_1C));
        Set<String> additions=new HashSet<>(expected); additions.removeAll(predecessor);
        assertEquals(Set.of("CONTACT"),additions);
        Set<String> removed=new HashSet<>(predecessor); removed.removeAll(expected);
        assertTrue(removed.isEmpty(),"the current successor must preserve every historical EntityType");
        Set<String> unexpected=new HashSet<>(Set.copyOf(currentTokens)); unexpected.removeAll(expected);
        assertTrue(unexpected.isEmpty(),"the current successor must accept no unexpected EntityTypes");
    }

    @Test void scriptsAreGuardedTransactionalAndVerificationIsReadOnly() throws Exception {
        String migration=Files.readString(MIGRATION), verify=Files.readString(VERIFY);
        for(String sql:List.of(migration,verify)) {
            assertTrue(sql.contains("DECLARE @OperatorVerifiedAllTenantVisibility bit = 0"));
            assertTrue(sql.contains("SESSION_CONTEXT(N'ShaleClientId') IS NOT NULL"));
            assertTrue(sql.contains("allowlist discovery is missing or ambiguous"));
            assertTrue(sql.contains("IF XACT_STATE() <> 0 ROLLBACK TRANSACTION"));
            assertFalse(sql.contains("\nGO\n"));
        }
        assertTrue(migration.contains("WITH CHECK ADD CONSTRAINT"));
        assertTrue(migration.contains("CHECK CONSTRAINT CK_EntityActionAuditLog_EntityType"));
        assertTrue(migration.contains("is_disabled = 0 AND is_not_trusted = 0) <> 1"));
        assertTrue(migration.contains("before_check.ObjectId <> @AllowlistObjectId"));
        assertTrue(migration.contains("after_check.definition = before_check.ConstraintDefinition"));
        assertTrue(migration.contains("COMMIT TRANSACTION"));
        assertTrue(migration.contains("THROW;"));
        assertTrue(verify.contains("Phase 2B CONTACT EntityType accepted (expect 1)"));
        String upper=verify.toUpperCase(Locale.ROOT);
        assertFalse(upper.matches("(?s).*ALTER\\s+TABLE.*"));
        assertFalse(upper.matches("(?s).*UPDATE\\s+DBO\\..*"));
        assertFalse(upper.matches("(?s).*DELETE\\s+FROM\\s+DBO\\..*"));
    }
}
