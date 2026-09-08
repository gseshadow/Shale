package com.shale.data.dao;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

final class OrganizationTypesFoundationMigrationContractTest {
    private static final Pattern GO = Pattern.compile("(?im)^\\s*GO\\s*$");
    private static Path repo(String path) {
        Path direct = Path.of(path);
        return Files.exists(direct) ? direct : Path.of("..").resolve(path);
    }
    private static String read(String path) throws Exception {
        return Files.readString(repo(path)).replace("\r\n", "\n").replace('\r', '\n');
    }
    private static String normalizeFilter(String filter) {
        return filter.toLowerCase(Locale.ROOT).replace("[", "").replace("]", "")
                .replace(" ", "").replace("\t", "").replace("\n", "")
                .replace("\r", "").replace("(", "").replace(")", "");
    }
    private static boolean validColor(String color) {
        return color != null && color.matches("#[0-9A-F]{6}") && color.equals(color.toUpperCase(Locale.ROOT));
    }
    private static boolean validSystemKey(String key) {
        return key != null && key.matches("[a-z][a-z0-9_]*") && key.equals(key.toLowerCase(Locale.ROOT));
    }
    private static String withoutSqlStringLiterals(String sql) {
        StringBuilder result = new StringBuilder(sql.length());
        boolean quoted = false;
        for (int i = 0; i < sql.length(); i++) {
            char ch = sql.charAt(i);
            if (ch == '\'' && quoted && i + 1 < sql.length() && sql.charAt(i + 1) == '\'') {
                result.append("  ");
                i++;
            } else if (ch == '\'') {
                quoted = !quoted;
                result.append(' ');
            } else {
                result.append(quoted ? ' ' : ch);
            }
        }
        assertFalse(quoted, "SQL string literal must be closed");
        return result.toString();
    }

    @Test void migrationPreservesBaselineAndBuildsDefinitionContract() throws Exception {
        String s = read("docs/sql/2026-09-08_organization_types_foundation_phase1a.sql");
        assertTrue(s.contains("DECLARE @IsFirstFoundationDeployment bit=CASE WHEN"));
        assertTrue(s.contains("IF @IsFirstFoundationDeployment=1 AND ((SELECT COUNT_BIG(*) FROM dbo.OrganizationTypes)<>7"));
        assertTrue(s.contains("IF @IsFirstFoundationDeployment=0 AND EXISTS"), "reruns tolerate later definitions while preserving the seven baseline identities");
        for (String row : new String[]{"(1,N'Provider',7)", "(7,N'Other',7)", "N''provider'',N''#0F766E'',0", "N''other'',N''#6B7280'',6"}) assertTrue(s.contains(row), row);
        assertTrue(s.contains("sys.default_constraints dc JOIN sys.columns c"));
        assertTrue(s.contains("ALTER COLUMN ShaleClientId int NULL"));
        for (String column : new String[]{"SystemKey", "Description", "Color", "SortOrder", "IsActive", "IsDeleted", "CreatedAt", "CreatedByUserId", "UpdatedAt", "UpdatedByUserId", "DeletedAt", "DeletedByUserId", "RowVer"}) assertTrue(s.contains("N'" + column + "'") || s.contains("ADD " + column), column);
        assertTrue(s.contains("UX_OrganizationTypes_Global_SystemKey"));
        assertTrue(s.contains("UX_OrganizationTypes_Tenant_SystemKey"));
        assertTrue(s.contains("Color COLLATE Latin1_General_100_BIN2=UPPER(Color) COLLATE Latin1_General_100_BIN2"));
        assertFalse(s.contains("INSERT dbo.OrganizationTypes"));
    }

    @Test void colorContractIsCaseSensitiveEvenWhenDatabaseDefaultIsNot() throws Exception {
        String s = read("docs/sql/2026-09-08_organization_types_foundation_phase1a.sql");
        String v = read("docs/sql/verification/2026-09-08_organization_types_foundation_phase1a_verification.sql");
        for (String sql : new String[]{s, v}) {
            assertTrue(sql.contains("DATALENGTH(Color)"));
            assertTrue(sql.contains("SUBSTRING(Color,2,6) COLLATE Latin1_General_100_BIN2"));
            assertTrue(sql.contains("UPPER(Color) COLLATE Latin1_General_100_BIN2"));
        }
        assertTrue(validColor("#ABCDEF"));
        assertFalse(validColor("#ABCDEF "));
        assertFalse(validColor("#abcdef"));
        assertFalse(validColor("#ABCDE"));
    }

    @Test void systemKeyContractIsBinaryLowercaseSnakeCase() throws Exception {
        String s = read("docs/sql/2026-09-08_organization_types_foundation_phase1a.sql");
        String v = read("docs/sql/verification/2026-09-08_organization_types_foundation_phase1a_verification.sql");
        for (String sql : new String[]{s, v}) {
            assertTrue(sql.contains("SystemKey COLLATE Latin1_General_100_BIN2"));
            assertTrue(sql.contains("LOWER(SystemKey) COLLATE Latin1_General_100_BIN2"));
            assertTrue(sql.contains("LEFT(SystemKey,1) COLLATE Latin1_General_100_BIN2"));
            assertTrue(sql.contains("systemkeycollatelatin1_general_100_bin2=lowersystemkeycollatelatin1_general_100_bin2"));
        }
        assertTrue(validSystemKey("provider"));
        assertTrue(validSystemKey("provider_2"));
        for (String invalid : new String[]{"Provider", "PROVIDER", "", "provider type", "provider-type", "provider!", "2provider"}) {
            assertFalse(validSystemKey(invalid), invalid);
        }
    }

    @Test void firstDeploymentDefersPostAddAndPostCreateBindingToDynamicSql() throws Exception {
        String s = read("docs/sql/2026-09-08_organization_types_foundation_phase1a.sql");
        assertTrue(s.contains("EXEC sys.sp_executesql @sql,N'@First bit',@IsFirstFoundationDeployment;"));
        assertTrue(s.contains("EXEC sys.sp_executesql @sql,N'@First bit',@IsFirstAssignmentDeployment;"));
        String staticSql = withoutSqlStringLiterals(s);
        assertFalse(staticSql.contains("UPDATE d SET SystemKey="));
        assertFalse(staticSql.contains("ALTER TABLE dbo.OrganizationTypes ALTER COLUMN SystemKey"));
        assertFalse(staticSql.contains("CREATE UNIQUE INDEX UX_OrganizationTypes_Global_SystemKey"));
        assertFalse(staticSql.contains("CREATE UNIQUE INDEX UX_OrganizationOrganizationTypes_Active"));
        assertFalse(staticSql.contains("INSERT dbo.OrganizationOrganizationTypes("));
        assertFalse(staticSql.contains("JOIN dbo.OrganizationOrganizationTypes a"));
    }

    @Test void successfulRerunNeverResetsMutableDefinitionState() throws Exception {
        String s = read("docs/sql/2026-09-08_organization_types_foundation_phase1a.sql");
        int rerun = s.indexOf("ELSE\nUPDATE d SET SystemKey=COALESCE");
        int complete = s.indexOf("IF EXISTS(SELECT 1 FROM (VALUES(1,N''provider'')", rerun);
        assertTrue(rerun > 0 && complete > rerun);
        String rerunBackfill = s.substring(rerun, complete);
        assertTrue(rerunBackfill.contains("Color=COALESCE(d.Color,N''#6C757D'')"));
        assertTrue(rerunBackfill.contains("SortOrder=COALESCE(d.SortOrder,v.SortOrder)"));
        assertTrue(rerunBackfill.contains("IsActive=COALESCE(d.IsActive"));
        assertTrue(rerunBackfill.contains("IsDeleted=COALESCE(d.IsDeleted"));
        assertFalse(rerunBackfill.contains("Color=v.Color"), "custom colors must survive reruns");
        assertFalse(rerunBackfill.contains("SortOrder=v.SortOrder"), "custom ordering must survive reruns");
        assertFalse(rerunBackfill.contains("IsActive=1"), "inactive definitions must survive reruns");
        assertFalse(rerunBackfill.contains("IsDeleted=0"), "removed definitions must survive reruns");
    }

    @Test void assignmentBackfillOwnershipHistoryAndCompatibilityAreEnforced() throws Exception {
        String s = read("docs/sql/2026-09-08_organization_types_foundation_phase1a.sql");
        assertTrue(s.contains("CREATE TABLE dbo.OrganizationOrganizationTypes"));
        assertTrue(s.contains("FOREIGN KEY(ShaleClientId,OrganizationId) REFERENCES dbo.Organizations(ShaleClientId,Id)"));
        assertTrue(s.contains("FOREIGN KEY(OrganizationTypeId) REFERENCES dbo.OrganizationTypes(OrganizationTypeId)"));
        assertTrue(s.contains("IsDeleted=1 AND IsPrimary=0"));
        assertTrue(s.contains("UX_OrganizationOrganizationTypes_ActivePrimary"));
        assertTrue(s.contains("WHERE IsDeleted=0 AND IsPrimary=1"));
        assertTrue(s.contains("INSERT dbo.OrganizationOrganizationTypes"));
        assertTrue(s.contains("NOT EXISTS(SELECT 1 FROM dbo.OrganizationOrganizationTypes"));
        String historicalGuard = s.substring(s.indexOf("WHERE NOT EXISTS(SELECT 1 FROM dbo.OrganizationOrganizationTypes"), s.indexOf("IF @First=1 AND"));
        assertFalse(historicalGuard.contains("IsDeleted"), "a deleted historical assignment must prevent foundation recreation");
        assertTrue(s.contains("did not create exactly 176 matching active primary assignments"));
        assertTrue(s.contains("#OrganizationBaseline"));
        assertFalse(s.contains("UPDATE dbo.Organizations"));
        assertFalse(s.contains("DELETE FROM"));
    }

    @Test void migrationUsesOneGuardedTransactionAndEstablishedRls() throws Exception {
        String s = read("docs/sql/2026-09-08_organization_types_foundation_phase1a.sql");
        assertTrue(s.contains("SET XACT_ABORT ON;"));
        assertTrue(s.indexOf("@OperatorVerifiedAllTenantVisibility bit=0") < s.indexOf("BEGIN TRANSACTION;"));
        assertTrue(s.contains("SESSION_CONTEXT(N'ShaleClientId') IS NOT NULL"));
        assertTrue(s.contains("USER_NAME() IN(N'shale_app',N'shale_runtime')"));
        assertTrue(s.contains("sec.fn_FilterByTenantOrGlobal"));
        assertTrue(s.contains("sec.fn_FilterByTenant"));
        assertTrue(s.contains("ALTER SECURITY POLICY "));
        assertFalse(s.contains("CREATE SECURITY POLICY"));
        assertTrue(s.contains("END TRY BEGIN CATCH IF @@TRANCOUNT>0 ROLLBACK TRANSACTION; THROW; END CATCH;"));
        assertEquals(1, GO.matcher(s).results().count());
    }

    @Test void verificationIsReadOnlyGuardedAndCoversRequiredFindings() throws Exception {
        String v = read("docs/sql/verification/2026-09-08_organization_types_foundation_phase1a_verification.sql");
        for (String finding : new String[]{"column contract mismatches", "ShaleClientId defaults still present", "unexpected mutation of baseline OrganizationType identity", "invalid SystemKeys", "duplicate global SystemKeys", "missing/incompatible required indexes", "missing/invalid assignment or actor foreign keys", "duplicate active assignments", "multiple active primary assignments", "assignment tenant mismatches", "definition tenant mismatches", "Organizations missing historical foundation assignment", "assignments not matching compatibility OrganizationTypeId", "live baseline Organization count changed", "missing/wrong/unexpected RLS predicates"}) assertTrue(v.contains(finding), finding);
        assertTrue(v.contains("expected 176"));
        assertTrue(v.contains("=171") && v.contains("=5"));
        assertTrue(v.contains("initial palette/order matches (informational"));
        assertTrue(v.contains("IF EXISTS(SELECT 1 FROM @Findings WHERE FindingCount<>0) THROW"));
        assertFalse(v.contains("ALTER TABLE"));
        assertFalse(v.contains("ALTER SECURITY POLICY"));
        assertFalse(v.contains("INSERT dbo."));
        assertFalse(GO.matcher(v).find());
    }

    @Test void verificationNormalizesCanonicalSqlServerFiltersButRejectsWrongSemantics() throws Exception {
        String v = read("docs/sql/verification/2026-09-08_organization_types_foundation_phase1a_verification.sql");
        for (String token : new String[]{"i.has_filter", "i.is_disabled", "i.is_hypothetical", "ic.key_ordinal", "i.NormalizedFilter<>e.f", "NCHAR(9)", "NCHAR(10)", "NCHAR(13)"}) assertTrue(v.contains(token), token);
        assertEquals("shaleclientidisnull", normalizeFilter("([ShaleClientId] IS NULL)"));
        assertEquals("isdeleted=0", normalizeFilter("([IsDeleted]=(0))"));
        assertEquals("isdeleted=0andisprimary=1", normalizeFilter("(([IsDeleted]=(0)) AND ([IsPrimary]=(1)))"));
        assertNotEquals("isdeleted=0", normalizeFilter("([IsDeleted]=(1))"));
        assertNotEquals("isdeleted=0andisprimary=1", normalizeFilter("([IsDeleted]=(0)) AND ([IsPrimary]=(0))"));
    }

    @Test void migrationRejectsEveryIncompatibleNamedPhaseOneAObjectBeforeCommit() throws Exception {
        String s = read("docs/sql/2026-09-08_organization_types_foundation_phase1a.sql");
        int validation = s.indexOf("DECLARE @RequiredIndexes TABLE");
        int commit = s.indexOf("COMMIT TRANSACTION;");
        assertTrue(validation > 0 && validation < commit);
        for (String indexContract : new String[]{"i.object_id=OBJECT_ID(N'dbo.'+e.TableName)", "i.NormalizedFilter<>e.NormalizedFilter", "ISNULL(x.Keys,N'')<>e.Keys", "ISNULL(x.Includes,N'')<>e.Includes", "i.is_unique<>e.IsUnique", "i.has_filter<>", "i.is_disabled=1", "i.is_hypothetical=1"}) assertTrue(s.contains(indexContract), indexContract);
        assertTrue(s.contains("Required Phase 1A index has incompatible target, keys, includes, uniqueness, filter, or state."));
        for (String fkContract : new String[]{"f.parent_object_id=OBJECT_ID(N'dbo.'+e.ChildTable)", "f.referenced_object_id<>OBJECT_ID(N'dbo.'+e.ParentTable)", "x.ChildColumns<>e.ChildColumns", "x.ParentColumns<>e.ParentColumns", "f.is_disabled=1", "f.is_not_trusted=1"}) assertTrue(s.contains(fkContract), fkContract);
        assertTrue(s.contains("Required Phase 1A foreign key has incompatible child, parent, ordered columns, or state."));
        for (String checkContract : new String[]{"c.parent_object_id=OBJECT_ID(N'dbo.'+e.TableName)", "c.NormalizedDefinition<>e.NormalizedDefinition", "c.is_disabled=1", "c.is_not_trusted=1"}) assertTrue(s.contains(checkContract), checkContract);
        assertTrue(s.contains("Required Phase 1A CHECK has incompatible target, semantic definition, or state."));
    }

    @Test void forbiddenPhaseOneCSurfacesRemainUntouched() throws Exception {
        String s = read("docs/sql/2026-09-08_organization_types_foundation_phase1a.sql");
        for (String table : new String[]{"CaseParties", "PartyRoles", "CaseOrganizations", "EntityActionAuditLog"}) assertFalse(s.contains(table), table);
        assertFalse(s.contains("Contacts.OrganizationId"));
    }
}
