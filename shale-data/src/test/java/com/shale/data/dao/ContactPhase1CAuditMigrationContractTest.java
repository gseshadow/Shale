package com.shale.data.dao;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

final class ContactPhase1CAuditMigrationContractTest {
    private static final Path VERIFY=Path.of("..","docs","sql","verification","2026-08-25_contacts_phase1c_audit_allowlist_verification.sql");

    @Test void latestConstraintIsExactChronologicalEffectiveVocabulary() throws Exception {
        String sql=AuditEntityTypeMigrationChain.read(AuditEntityTypeMigrationChain.PHASE_1C);
        Set<String> expected=Arrays.stream(EntityActionAuditEvent.EntityType.values()).map(Enum::name).collect(Collectors.toSet());
        expected.add("CALENDAR_CASE_DATE_TYPE_MAPPING");
        assertEquals(expected,AuditEntityTypeMigrationChain.declaredAllowlist(sql));
        assertEquals(AuditEntityTypeMigrationChain.PHASE_1C_ADDITIONS.stream().filter(sql::contains).count(),6);
    }

    @Test void migrationUsesValidatedDiscoveryAndPreservesUnrelatedChecks() throws Exception {
        String sql=AuditEntityTypeMigrationChain.read(AuditEntityTypeMigrationChain.PHASE_1C);
        int ambiguity=sql.indexOf("allowlist discovery is missing or ambiguous");
        int unexpected=sql.indexOf("unexpected token; no constraints were changed");
        int rows=sql.indexOf("Existing EntityActionAuditLog rows contain an EntityType");
        int drop=sql.indexOf("ALTER TABLE dbo.EntityActionAuditLog DROP CONSTRAINT");
        assertTrue(ambiguity>0&&ambiguity<unexpected&&unexpected<rows&&rows<drop);
        assertTrue(sql.contains("QUOTENAME(@AllowlistName)"));
        assertTrue(sql.contains("before_check.ObjectId <> @AllowlistObjectId"));
        assertTrue(sql.contains("after_check.definition = before_check.ConstraintDefinition"));
        assertFalse(sql.contains("DECLARE @Current sysname"));
        assertFalse(sql.toUpperCase().matches("(?s).*UPDATE\\s+DBO\\.ENTITYACTIONAUDITLOG.*"));
        assertFalse(sql.toUpperCase().matches("(?s).*DELETE\\s+FROM\\s+DBO\\.ENTITYACTIONAUDITLOG.*"));
    }

    @Test void migrationIsSingleGuardedRollbackSafeAzureSqlBatch() throws Exception {
        String sql=AuditEntityTypeMigrationChain.read(AuditEntityTypeMigrationChain.PHASE_1C);
        assertEquals(0,count(sql,"\\nGO\\n"));
        assertTrue(sql.contains("DECLARE @OperatorVerifiedAllTenantVisibility bit = 0"));
        assertTrue(sql.contains("SESSION_CONTEXT(N'ShaleClientId') IS NOT NULL"));
        assertTrue(sql.contains("USER_NAME() IN (N'shale_app', N'shale_runtime')"));
        assertTrue(sql.indexOf("Operator-verified all-tenant visibility")<sql.indexOf("BEGIN TRANSACTION"));
        assertTrue(sql.contains("WITH CHECK ADD CONSTRAINT"));
        assertTrue(sql.contains("IF XACT_STATE() <> 0 ROLLBACK TRANSACTION"));
        assertFalse(sql.contains("predicate_object_id"));
    }

    @Test void verificationIsReadOnlyGuardedAndChecksEffectiveState() throws Exception {
        String sql=Files.readString(VERIFY), upper=sql.toUpperCase();
        assertEquals(0,count(sql,"\\nGO\\n"));
        assertTrue(sql.contains("DECLARE @OperatorVerifiedAllTenantVisibility bit = 0"));
        assertTrue(sql.contains("enabled trusted positive EntityType allowlists (expect 1)"));
        assertTrue(sql.contains("missing current or historical EntityTypes (expect 0)"));
        assertTrue(sql.contains("unexpected EntityTypes accepted (expect 0)"));
        assertTrue(sql.contains("Phase 1C Contact EntityTypes accepted (expect 6)"));
        assertTrue(sql.contains("existing audit rows outside effective allowlist (expect 0)"));
        assertTrue(sql.contains("unrelated EntityType CHECK constraints retained (inventory)"));
        assertFalse(upper.matches("(?s).*ALTER\\s+TABLE.*"));
        assertFalse(upper.matches("(?s).*UPDATE\\s+DBO\\..*"));
        assertFalse(upper.matches("(?s).*DELETE\\s+FROM\\s+DBO\\..*"));
        assertFalse(upper.matches("(?s).*INSERT\\s+(?:INTO\\s+)?DBO\\..*"));
        assertFalse(upper.contains("SP_SET_SESSION_CONTEXT"));
        assertFalse(upper.matches("(?s).*(GRANT|REVOKE|DENY)\\s+.*"));
    }

    private static int count(String text,String regex){return text.split(regex,-1).length-1;}
}
