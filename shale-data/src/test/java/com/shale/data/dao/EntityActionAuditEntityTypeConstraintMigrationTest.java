package com.shale.data.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

final class EntityActionAuditEntityTypeConstraintMigrationTest {
    private static final Path MIGRATION = Path.of("..", "docs", "sql",
            "2026-08-14_entity_action_audit_entity_type_constraint_case_status.sql");
    private static final Set<String> HISTORICALLY_DEPLOYED = Set.of(
            "CASE", "LINK_TYPE", "CASE_LINK", "CASE_LINK_SHARE", "MATERIAL_TYPE", "MATERIAL_REQUEST",
            "MATERIAL_REQUEST_FOLLOW_UP", "MATERIAL_ITEM", "CASE_DATE", "CASE_DATE_TYPE", "CALENDAR_EVENT",
            "CASE_DATE_ROLE_MAPPING", "CALENDAR_CASE_DATE_TYPE_MAPPING", "FORM_CONFIGURATION", "USER");

    private static String sql() throws Exception { return Files.readString(MIGRATION); }

    @Test
    void acceptsEveryEmittedAndPreviouslyDeployedEntityType() throws Exception {
        Set<String> emitted = Arrays.stream(EntityActionAuditEvent.EntityType.values())
                .map(Enum::name).collect(Collectors.toSet());
        Set<String> seeded = seededValues(sql());
        assertTrue(seeded.containsAll(emitted), "missing emitted EntityTypes: " + difference(emitted, seeded));
        assertTrue(seeded.containsAll(HISTORICALLY_DEPLOYED),
                "missing historically deployed EntityTypes: " + difference(HISTORICALLY_DEPLOYED, seeded));
        assertEquals(Set.of("CALENDAR_CASE_DATE_TYPE_MAPPING"), difference(seeded, emitted),
                "the deployed constraint retains only the retired historical mapping type beyond active writers");
    }

    @Test
    void preservesUnrelatedEntityTypeChecksAndReplacesExactlyOneValidatedAllowlist() throws Exception {
        String migration = sql();
        assertTrue(migration.contains("IsPositiveAllowlist"));
        assertTrue(migration.contains("COUNT(*) FROM @Checks WHERE IsPositiveAllowlist = 1) <> 1"));
        assertTrue(migration.contains("@AllowlistName"));
        assertTrue(migration.contains("QUOTENAME(@AllowlistName)"));
        assertFalse(migration.contains("QUOTENAME(@ConstraintName)"), "must not loop over and drop every check");
        assertTrue(migration.contains("before_check.ObjectId <> @AllowlistObjectId"));
        assertTrue(migration.contains("after_check.definition = before_check.ConstraintDefinition"));
        assertTrue(migration.contains("after_check.is_disabled = before_check.IsDisabled"));
        assertTrue(migration.contains("after_check.is_not_trusted = before_check.IsNotTrusted"));
    }

    @Test
    void parsesOnlyCompletePositiveInOrEqualityAllowlistGrammar() throws Exception {
        String migration = sql();
        assertTrue(migration.contains("LEFT(@Expression, 12) = N'ENTITYTYPEIN'"));
        assertTrue(migration.contains("LEFT(@Expression, 11) = N'ENTITYTYPE='"));
        assertTrue(migration.contains("IF LEFT(@Work, 1) <> N',' SET @Valid = 0"));
        assertTrue(migration.contains("IF LEFT(@Work, 2) <> N'OR' SET @Valid = 0"));
        assertTrue(migration.contains("LIKE N'%[^A-Z_]%'"));
        assertTrue(migration.contains("ELSE SET @Valid = 0"));
        assertTrue(migration.contains("DELETE FROM @Extracted WHERE ConstraintObjectId = @CheckId"));
        assertTrue(migration.contains("Value = 'LINK_TYPE'"));
        assertTrue(migration.contains("Value = 'CASE_LINK'"));
        assertTrue(migration.contains("Value = 'CASE_LINK_SHARE'"));
        assertFalse(recognizedByDocumentedGrammar("EntityType NOT IN ('EVIL')"));
        assertFalse(recognizedByDocumentedGrammar("EntityType <> 'EVIL'"));
        assertFalse(recognizedByDocumentedGrammar("LEN(EntityType) > 0"));
        assertFalse(recognizedByDocumentedGrammar("EntityType = 'CASE' AND 1 = 1"));
        assertFalse(recognizedByDocumentedGrammar("EntityType IN ('CASE', broken)"));
        assertTrue(recognizedByDocumentedGrammar("EntityType IN ('CASE','CALENDAR_EVENT')"));
        assertTrue(recognizedByDocumentedGrammar("EntityType='CASE' OR EntityType='USER'"));
    }

    @Test
    void ambiguityMalformedDiscoveryAndIncompatibleRowsFailBeforeDdl() throws Exception {
        String migration = sql();
        int ambiguityGuard = migration.indexOf("allowlist discovery is missing or ambiguous");
        int dataGuard = migration.indexOf("Existing EntityActionAuditLog rows contain an EntityType");
        int drop = migration.indexOf("ALTER TABLE dbo.EntityActionAuditLog DROP CONSTRAINT");
        assertTrue(ambiguityGuard > 0 && ambiguityGuard < drop);
        assertTrue(dataGuard > ambiguityGuard && dataGuard < drop);
        assertTrue(migration.contains("NOT EXISTS (SELECT 1 FROM @Allowed AS a WHERE a.Value = audit_row.EntityType)"));
        assertFalse(migration.toUpperCase().matches("(?s).*UPDATE\\s+DBO\\.ENTITYACTIONAUDITLOG.*"));
        assertFalse(migration.toUpperCase().matches("(?s).*DELETE\\s+FROM\\s+DBO\\.ENTITYACTIONAUDITLOG.*"));
    }

    @Test
    void rerunIsTransactionalRollbackSafeAndProducesOneTrustedCanonicalConstraint() throws Exception {
        String migration = sql();
        assertTrue(migration.contains("SET XACT_ABORT ON"));
        assertTrue(Pattern.compile("(?is)\\bBEGIN\\s+TRY\\s+BEGIN\\s+TRANSACTION\\s*;")
                .matcher(migration).find(), "the TRY body must open the explicit transaction before other work");
        assertTrue(migration.contains("WITH CHECK ADD CONSTRAINT"));
        assertTrue(migration.contains("CHECK CONSTRAINT CK_EntityActionAuditLog_EntityType"));
        assertTrue(migration.contains("name = N'CK_EntityActionAuditLog_EntityType'"));
        assertTrue(migration.contains("is_disabled = 0 AND is_not_trusted = 0) <> 1"));
        assertTrue(migration.contains("COMMIT TRANSACTION"));
        assertTrue(migration.contains("IF XACT_STATE() <> 0 ROLLBACK TRANSACTION"));
        assertTrue(migration.contains("THROW;"));
        assertFalse(migration.contains("CREATE TABLE dbo.EntityActionAuditLog"));
        assertFalse(migration.contains("2026-07-20_entity_action_audit_phase61.sql"));
    }

    private static boolean recognizedByDocumentedGrammar(String expression) {
        String normalized = expression.toUpperCase().replaceAll("[\\s\\[\\]()]", "");
        String value = "'[A-Z_]+'";
        return normalized.matches("ENTITYTYPEIN" + value + "(?:," + value + ")*")
                || normalized.matches("ENTITYTYPE=" + value + "(?:ORENTITYTYPE=" + value + ")*");
    }

    private static Set<String> seededValues(String migration) {
        int start = migration.indexOf("INSERT @Allowed (Value) VALUES");
        int end = migration.indexOf(";", start);
        Matcher matcher = Pattern.compile("\\('([A-Z_]+)'\\)").matcher(migration.substring(start, end));
        return matcher.results().map(result -> result.group(1)).collect(Collectors.toSet());
    }

    private static Set<String> difference(Set<String> expected, Set<String> actual) {
        return expected.stream().filter(value -> !actual.contains(value)).collect(Collectors.toSet());
    }
}
