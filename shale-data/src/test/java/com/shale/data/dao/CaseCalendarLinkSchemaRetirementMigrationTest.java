package com.shale.data.dao;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class CaseCalendarLinkSchemaRetirementMigrationTest {
    private static String sql;
    private static String compactSql;
    private static String foundation;

    @BeforeAll static void load() throws Exception {
        sql = normalize(Files.readString(Path.of("../docs/sql/2026-08-18_retire_calendar_case_date_link_schema.sql")));
        compactSql = sql.replaceAll("\\s+", " ");
        foundation = normalize(Files.readString(Path.of("../docs/sql/2026-08-11_case_date_calendar_link_foundation_step1.sql")));
    }

    @Test void supportsOnlyCompletePresentAndCompleteAbsentStates() {
        assertAll(
                () -> assertTrue(sql.contains("@MappingId IS NULL AND @LinkColumnId IS NOT NULL")),
                () -> assertTrue(sql.contains("@MappingId IS NOT NULL AND @LinkColumnId IS NULL")),
                () -> assertTrue(sql.contains("IF @Present=0 BEGIN")),
                () -> assertTrue(sql.contains("COMMIT TRANSACTION;\n    RETURN;")),
                () -> assertTrue(sql.contains("column shape is incomplete or incompatible")),
                () -> assertTrue(compactSql.contains("(SELECT COUNT(*) FROM sys.foreign_keys WHERE parent_object_id=@MappingId)<>5")),
                () -> assertTrue(compactSql.contains("fk.is_disabled=1 OR fk.is_not_trusted=1")),
                () -> assertTrue(compactSql.contains("(SELECT COUNT(*) FROM sys.indexes WHERE object_id=@MappingId AND index_id>0)<>3")),
                () -> assertTrue(compactSql.contains("(SELECT COUNT(*) FROM sys.default_constraints WHERE parent_object_id=@MappingId)<>4")),
                () -> assertTrue(compactSql.contains("(SELECT COUNT(*) FROM sys.check_constraints WHERE parent_object_id=@MappingId)<>1")),
                () -> assertTrue(compactSql.contains("FROM sys.sql_expression_dependencies d WHERE d.referenced_id=OBJECT_ID(N'dbo.CalendarEvents') AND d.referenced_minor_id=@LinkColumnId")),
                () -> assertTrue(compactSql.contains("AND NOT (d.referencing_id=OBJECT_ID(N'dbo.CalendarEvents') AND d.referencing_minor_id=@LinkIndexId)")));
    }

    @Test void requiresAdministrativeAllTenantVisibilityAndLocksBothWriteSurfaces() {
        assertAll(
                () -> assertTrue(sql.contains("SESSION_CONTEXT(N'ShaleClientId') IS NOT NULL")),
                () -> assertTrue(sql.contains("IS_SRVROLEMEMBER(N'sysadmin')")),
                () -> assertTrue(sql.contains("IS_MEMBER(N'db_owner')")),
                () -> assertTrue(sql.contains("CalendarCaseDateTypeMappings WITH (TABLOCKX,HOLDLOCK)")),
                () -> assertTrue(sql.contains("CalendarEvents WITH (TABLOCKX,HOLDLOCK)")),
                () -> assertFalse(sql.contains("TOP (0)")),
                () -> assertTrue(sql.contains("@MappingLockProbe=CHECKSUM_AGG(BINARY_CHECKSUM(Id))")),
                () -> assertTrue(sql.contains("@EventLockProbe=CHECKSUM_AGG(BINARY_CHECKSUM(CalendarEventId))")),
                () -> assertTrue(sql.indexOf("TABLOCKX,HOLDLOCK") < sql.indexOf("DROP FILTER PREDICATE")));
    }

    @Test void administrativeRolesAreNotTrustedAsRlsBypassAndUnfilteredMappingCheckIsOrdered() {
        int exactRlsValidation = sql.indexOf("COUNT(*) FROM sys.security_predicates WHERE target_object_id=@MappingId)<>4");
        int unrelatedSnapshot = sql.indexOf("INSERT @UnrelatedPredicates");
        int mappingLock = sql.indexOf("CalendarCaseDateTypeMappings WITH (TABLOCKX,HOLDLOCK)");
        int eventLock = sql.indexOf("CalendarEvents WITH (TABLOCKX,HOLDLOCK)");
        int filterDrop = sql.indexOf("DROP FILTER PREDICATE ON dbo.CalendarCaseDateTypeMappings");
        int mappingRows = sql.indexOf("IF EXISTS(SELECT 1 FROM dbo.CalendarCaseDateTypeMappings)");
        int firstBlockDrop = sql.indexOf("DROP BLOCK PREDICATE ON dbo.CalendarCaseDateTypeMappings AFTER INSERT");
        int triggerDrop = sql.indexOf("DROP TRIGGER dbo.TR_CalendarCaseDateTypeMappings_Tenant");
        assertAll(
                () -> assertTrue(sql.contains("Administrative role membership is a deployment permission requirement, not an RLS bypass")),
                () -> assertTrue(sql.contains("IF EXISTS(SELECT 1 FROM sys.security_predicates WHERE target_object_id=OBJECT_ID(N'dbo.CalendarEvents'))")),
                () -> assertEquals(1, occurrences(sql, "IF EXISTS(SELECT 1 FROM dbo.CalendarCaseDateTypeMappings)")),
                () -> assertTrue(exactRlsValidation < unrelatedSnapshot),
                () -> assertTrue(unrelatedSnapshot < mappingLock && mappingLock < eventLock),
                () -> assertTrue(eventLock < filterDrop && filterDrop < mappingRows),
                () -> assertTrue(mappingRows < firstBlockDrop && firstBlockDrop < triggerDrop),
                () -> assertTrue(sql.contains("CATCH rollback, which restores the predicate atomically")));
    }

    @Test void repeatsZeroDataAndLinkAnomalyPreflightsInsideTransaction() {
        assertAll(
                () -> assertTrue(sql.contains("WHERE CaseDateId IS NOT NULL")),
                () -> assertTrue(sql.contains("IF EXISTS(SELECT 1 FROM dbo.CalendarCaseDateTypeMappings)")),
                () -> assertTrue(sql.contains("d.ShaleClientId<>e.ShaleClientId")),
                () -> assertTrue(sql.contains("e.CaseId IS NULL OR d.CaseId<>e.CaseId")),
                () -> assertTrue(sql.contains("d.CaseId<>e.CaseId")),
                () -> assertTrue(sql.contains("GROUP BY ShaleClientId,CaseDateId HAVING COUNT_BIG(*)>1")));
    }

    @Test void removesExactlyFourMappingPredicatesAndPreservesThePolicy() {
        assertAll(
                () -> assertTrue(sql.contains("COUNT(*) FROM sys.security_predicates WHERE target_object_id=@MappingId)<>4")),
                () -> assertEquals(4, occurrences(sql, "ALTER SECURITY POLICY sec.TenantFilter DROP")),
                () -> assertTrue(sql.contains("@UnrelatedPredicates")),
                () -> assertTrue(sql.contains("EXCEPT SELECT security_predicate_id")),
                () -> assertTrue(sql.contains("@UnrelatedPredicates TABLE(security_predicate_id")),
                () -> assertFalse(sql.matches("(?s).*\\bpredicate_id\\b.*")),
                () -> assertTrue(sql.contains("object_id=@PolicyId AND is_enabled=1")),
                () -> assertFalse(sql.matches("(?is).*ALTER SECURITY POLICY.*STATE\s*=\s*OFF.*")));
    }

    @Test void followsRequiredDependencyOrderAndUsesIndexMetadataForTheCatalogCaution() {
        int rls = sql.indexOf("DROP FILTER PREDICATE");
        int trigger = sql.indexOf("DROP TRIGGER");
        int mappingFks = sql.indexOf("DROP CONSTRAINT FK_CalendarCaseDateTypeMappings");
        int mappingIndexes = sql.indexOf("DROP INDEX UX_CalendarCaseDateTypeMappings");
        int mappingTable = sql.indexOf("DROP TABLE dbo.CalendarCaseDateTypeMappings");
        int linkFk = sql.indexOf("DROP CONSTRAINT FK_CalendarEvents_CaseDate_Tenant");
        int linkIndexes = sql.indexOf("DROP INDEX UX_CalendarEvents_ActiveCaseDateLink ON dbo.CalendarEvents");
        int linkColumn = sql.indexOf("DROP COLUMN CaseDateId");
        assertTrue(rls < trigger && trigger < mappingFks && mappingFks < mappingIndexes
                && mappingIndexes < mappingTable && mappingTable < linkFk && linkFk < linkIndexes && linkIndexes < linkColumn);
        assertAll(
                () -> assertTrue(sql.matches("(?is).*FROM\\s+sys\\.indexes\\s+i.*?FROM\\s+sys\\.index_columns\\s+ic\\s+WHERE\\s+ic\\.object_id\\s*=\\s*i\\.object_id\\s+AND\\s+ic\\.index_id\\s*=\\s*i\\.index_id.*")),
                () -> assertTrue(sql.contains("referencing_minor_id=@LinkIndexId")),
                () -> assertTrue(sql.contains("index id (13 in the inventory)")),
                () -> assertTrue(sql.contains("Exactly one CalendarEvents index may involve CaseDateId")),
                () -> assertTrue(sql.contains("N'ShaleClientId,CaseDateId'")),
                () -> assertTrue(sql.contains("N'casedateidisnotnull'")),
                () -> assertTrue(sql.contains("is_included_column=1")),
                () -> assertFalse(sql.contains("@DropIndexes")),
                () -> assertFalse(sql.contains("STRING_AGG(N'DROP INDEX '")),
                () -> assertFalse(sql.matches("(?is).*DROP\s+COLUMN\s+SourceId.*")));
    }

    @Test void validatesExactForeignKeysMappingObjectsAndExpectedIndexDependency() {
        assertAll(
                () -> assertTrue(sql.contains("@ExpectedMappingFks")),
                () -> assertTrue(sql.contains("ReferencedSchema sysname")),
                () -> assertTrue(sql.contains("OBJECT_SCHEMA_NAME(fk.referenced_object_id)<>e.ReferencedSchema")),
                () -> assertTrue(sql.contains("OBJECT_NAME(fk.referenced_object_id)<>e.ReferencedTable")),
                () -> assertTrue(sql.contains("pc.name=e.ParentColumn AND rc.name=e.ReferencedColumn")),
                () -> assertTrue(sql.contains("OBJECT_SCHEMA_NAME(fk.parent_object_id)=N'dbo'")),
                () -> assertTrue(sql.contains("OBJECT_SCHEMA_NAME(fk.referenced_object_id)=N'dbo'")),
                () -> assertTrue(sql.contains("constraint_object_id=fk.object_id)=1")),
                () -> assertTrue(sql.contains("=N'CaseDateId>Id'")),
                () -> assertFalse(sql.contains("N'ShaleClientId>ShaleClientId,CaseDateId>Id'")),
                () -> assertTrue(sql.contains("REPLACE(d.definition,N' ',N'')=N'((0))'")),
                () -> assertTrue(sql.contains("CK_CalendarCaseDateTypeMappings_Direction")),
                () -> assertTrue(sql.contains("@TriggerDefinition NOT LIKE")),
                () -> assertTrue(sql.contains("NOT (d.referencing_id=OBJECT_ID(N'dbo.CalendarEvents') AND d.referencing_minor_id=@LinkIndexId)")),
                () -> assertFalse(sql.contains("Unexpected expression dependency targets CalendarEvents.CaseDateId")));
    }

    @Test void isAtomicForwardOnlyAndContainsNoBusinessOrAuditDml() {
        assertAll(
                () -> assertTrue(sql.contains("SET XACT_ABORT ON")),
                () -> assertTrue(sql.contains("BEGIN TRY\n  BEGIN TRANSACTION;")),
                () -> assertTrue(sql.contains("IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION")),
                () -> assertFalse(sql.matches("(?is).*(?:UPDATE|DELETE|MERGE|TRUNCATE)\\s+(?:dbo\\.)?(?:CalendarEvents|CaseDates|CalendarCaseDateTypeMappings|AuditLog|EntityActionAuditLog).*")),
                () -> assertFalse(sql.matches("(?is).*INSERT\\s+(?:INTO\\s+)?(?:dbo\\.)?(?:CalendarEvents|CaseDates|CalendarCaseDateTypeMappings|AuditLog|EntityActionAuditLog).*")),
                () -> assertFalse(sql.toLowerCase().contains("backfill")));
    }

    @Test void retainsRequiredObjectsAndVerifiesPostconditions() {
        assertAll(
                () -> assertTrue(sql.contains("UX_CaseDates_ShaleClientId_Id is missing or incompatible")),
                () -> assertTrue(sql.contains("CalendarEvents.RowVer is missing or incompatible")),
                () -> assertFalse(sql.contains("DROP INDEX UX_CaseDates_ShaleClientId_Id")),
                () -> assertFalse(sql.contains("DROP COLUMN RowVer")),
                () -> assertTrue(sql.contains("Retired schema postcondition failed")));
    }

    @Test void historicalFoundationMigrationRemainsImmutable() {
        assertEquals("5853ef79f6493e9dd066edc7f1626c1206c1796f7087ca7ef3177670deff92e9", sha256(foundation));
    }

    private static int occurrences(String value, String needle) {
        int count = 0;
        for (int at = 0; (at = value.indexOf(needle, at)) >= 0; at += needle.length()) count++;
        return count;
    }

    private static String normalize(String value) {
        return value.replace("\r\n", "\n").replace('\r', '\n');
    }

    private static String sha256(String value) {
        try {
            var digest = java.security.MessageDigest.getInstance("SHA-256").digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }
}
