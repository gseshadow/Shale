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
            "2026-08-12_entity_action_audit_entity_type_constraint.sql");
    private static final Set<String> HISTORICALLY_DEPLOYED = Set.of(
            "CASE", "LINK_TYPE", "CASE_LINK", "CASE_LINK_SHARE", "MATERIAL_TYPE", "MATERIAL_REQUEST",
            "MATERIAL_REQUEST_FOLLOW_UP", "MATERIAL_ITEM", "CASE_DATE", "CALENDAR_EVENT",
            "CASE_DATE_ROLE_MAPPING", "CALENDAR_CASE_DATE_TYPE_MAPPING", "FORM_CONFIGURATION", "USER");

    private static String sql() throws Exception {
        return Files.readString(MIGRATION);
    }

    @Test
    void acceptsEveryEmittedAndPreviouslyDeployedEntityType() throws Exception {
        Set<String> emitted = Arrays.stream(EntityActionAuditEvent.EntityType.values())
                .map(Enum::name).collect(Collectors.toSet());
        Set<String> seeded = seededValues(sql());
        assertTrue(seeded.containsAll(emitted), "missing emitted EntityTypes: " + difference(emitted, seeded));
        assertTrue(seeded.containsAll(HISTORICALLY_DEPLOYED),
                "missing historically deployed EntityTypes: " + difference(HISTORICALLY_DEPLOYED, seeded));
        assertEquals(emitted, seeded, "the explicit release vocabulary must track production exactly");
    }

    @Test
    void isForwardOnlyTransactionalIdempotentTrustedAndRollsBackOnFailure() throws Exception {
        String migration = sql();
        assertTrue(migration.contains("SET XACT_ABORT ON"));
        assertTrue(migration.contains("BEGIN TRY\n    BEGIN TRANSACTION"));
        assertTrue(migration.contains("COMMIT TRANSACTION"));
        assertTrue(migration.contains("IF XACT_STATE() <> 0 ROLLBACK TRANSACTION"));
        assertTrue(migration.contains("THROW;"));
        assertTrue(migration.contains("WITH CHECK ADD CONSTRAINT"));
        assertTrue(migration.contains("CHECK CONSTRAINT CK_EntityActionAuditLog_EntityType"));
        assertTrue(migration.contains("cc.is_disabled = 0"));
        assertTrue(migration.contains("cc.is_not_trusted = 0"));
        assertTrue(migration.contains("sys.sql_expression_dependencies"));
        assertTrue(migration.contains("dep.referenced_minor_id = @EntityTypeColumnId"));
        assertTrue(migration.contains("QUOTENAME(@ConstraintName)"));
        assertTrue(migration.contains("Preserve every quoted value allowed"));
        assertFalse(migration.contains("2026-07-20_entity_action_audit_phase61.sql"));
        assertFalse(migration.contains("2026-08-03_users_management_completion.sql"));
    }

    @Test
    void neverMutatesAuditDataOrCreatesAnotherAuditTable() throws Exception {
        String migration = sql().toUpperCase();
        assertFalse(Pattern.compile("\\b(UPDATE|DELETE|MERGE|TRUNCATE)\\s+(?:TABLE\\s+)?DBO\\.ENTITYACTIONAUDITLOG\\b")
                .matcher(migration).find());
        assertFalse(Pattern.compile("\\bINSERT\\s+(?:INTO\\s+)?DBO\\.ENTITYACTIONAUDITLOG\\b")
                .matcher(migration).find());
        assertFalse(migration.contains("CREATE TABLE DBO.ENTITYACTIONAUDITLOG"));
        assertEquals(1, Pattern.compile("ALTER TABLE DBO\\.ENTITYACTIONAUDITLOG WITH CHECK ADD CONSTRAINT")
                .matcher(migration).results().count());
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
