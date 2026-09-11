package com.shale.data.dao;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

final class OrganizationStructuredContactMethodsMigrationContractTest {
    private static final Pattern GO = Pattern.compile("(?im)^\\s*GO\\s*$");
    private static Path repo(String path) {
        Path direct = Path.of(path);
        return Files.exists(direct) ? direct : Path.of("..").resolve(path);
    }
    private static String read(String path) throws Exception {
        return Files.readString(repo(path)).replace("\r\n", "\n").replace('\r', '\n');
    }

    @Test void migrationIsGuardedTransactionalAndFailsClosedByDefault() throws Exception {
        String sql = read("docs/sql/2026-09-10_organizations_phase3a_structured_contact_methods.sql");
        assertTrue(sql.contains("SET XACT_ABORT ON;") && sql.contains("BEGIN TRY") && sql.contains("BEGIN TRANSACTION;"));
        assertTrue(sql.contains("@ExpectedDatabase sysname=N'REPLACE_WITH_APPROVED_DATABASE'") && sql.contains("DB_NAME()<>@ExpectedDatabase"));
        assertTrue(sql.contains("@OperatorVerifiedAllTenantVisibility bit=0"));
        assertTrue(sql.contains("SESSION_CONTEXT(N'ShaleClientId') IS NOT NULL") && sql.contains("SESSION_CONTEXT(N'PrincipalUserId') IS NOT NULL"));
        assertTrue(sql.contains("USER_NAME() IN(N'shale_app',N'shale_runtime')"));
        assertTrue(sql.contains("END TRY BEGIN CATCH IF XACT_STATE()<>0 ROLLBACK TRANSACTION; THROW; END CATCH;"));
        assertEquals(1, GO.matcher(sql).results().count());
        assertFalse(sql.contains("ALTER SECURITY POLICY [TenantFilter] WITH (STATE = OFF)"));
        assertFalse(sql.matches("(?is).*ALTER\\s+SECURITY\\s+POLICY.*STATE\\s*=\\s*OFF.*"));
    }

    @Test void allFourTablesHaveLifecycleOrderingAndRowVersion() throws Exception {
        String sql = read("docs/sql/2026-09-10_organizations_phase3a_structured_contact_methods.sql");
        for (String table : new String[]{"OrganizationPhoneNumbers", "OrganizationEmailAddresses", "OrganizationAddresses", "OrganizationWebsites"}) {
            assertTrue(sql.contains("CREATE TABLE dbo." + table), table);
            assertTrue(sql.contains("N'" + table + "'"), table);
        }
        for (String field : new String[]{"ShaleClientId", "OrganizationId", "Kind", "IsPrimary", "SortOrder", "IsDeleted", "CreatedAt", "CreatedByUserId", "UpdatedAt", "UpdatedByUserId", "DeletedAt", "DeletedByUserId", "RowVer rowversion NOT NULL"})
            assertTrue(sql.contains(field), field);
        assertEquals(4, count(sql, "CHECK(SortOrder>=0)"));
        assertEquals(4, count(sql, "IsDeleted=1 AND IsPrimary=0 AND DeletedAt IS NOT NULL AND DeletedByUserId IS NOT NULL"));
    }

    @Test void tenantOwnershipRlsPrimaryAndDuplicatesAreDatabaseEnforced() throws Exception {
        String sql = read("docs/sql/2026-09-10_organizations_phase3a_structured_contact_methods.sql");
        assertTrue(sql.contains("FOREIGN KEY(ShaleClientId,OrganizationId) REFERENCES dbo.Organizations(ShaleClientId,Id)"));
        assertTrue(sql.contains("f.delete_referential_action<>0") && !sql.contains("ON DELETE CASCADE"));
        assertTrue(sql.contains("ADD FILTER PREDICATE sec.fn_FilterByTenant(ShaleClientId)"));
        assertTrue(sql.contains("sys.security_predicates spr JOIN sys.security_policies sp ON spr.object_id=sp.object_id"));
        assertFalse(sql.contains("security_policy_id"));
        assertTrue(sql.contains("WHERE IsDeleted=0 AND IsPrimary=1"));
        for (String index : new String[]{"UX_OrganizationPhoneNumbers_ActiveValue", "UX_OrganizationEmailAddresses_ActiveValue", "UX_OrganizationWebsites_ActiveValue"})
            assertTrue(sql.contains(index), index);
        assertTrue(sql.contains("(ShaleClientId,OrganizationId,IsDeleted,SortOrder,Id)"));
    }

    @Test void legacyBackfillMapsEveryConceptAndNeverRecreatesDeletedHistory() throws Exception {
        String sql = read("docs/sql/2026-09-10_organizations_phase3a_structured_contact_methods.sql");
        for (String legacy : new String[]{"Phone", "Fax", "Email", "Website", "Address1", "Address2", "City", "State", "PostalCode", "Country"})
            assertTrue(sql.contains("N'" + legacy + "'"), legacy);
        assertTrue(sql.contains("N''WORK'' Kind,LTRIM(RTRIM(Phone)) Value,0 SortOrder"));
        assertTrue(sql.contains("N''FAX'',LTRIM(RTRIM(Fax)),1"));
        assertTrue(sql.contains("s.Kind=N''WORK'' OR NOT EXISTS(SELECT 1 FROM dbo.Organizations o"),
                "fax is primary only when no legacy voice phone or structured primary exists");
        assertTrue(sql.contains("LOWER(LTRIM(RTRIM(o.Email)))"));
        assertTrue(sql.contains("NULLIF(LTRIM(RTRIM(o.Address1)),N'''')") && sql.contains("NULLIF(LTRIM(RTRIM(o.Country)),N'''')"));
        assertTrue(sql.contains("N''MAIN'',LTRIM(RTRIM(o.Website))"));
        for (String guard : new String[]{
                "NOT EXISTS(SELECT 1 FROM dbo.OrganizationEmailAddresses e WHERE e.ShaleClientId=o.ShaleClientId AND e.OrganizationId=o.Id AND e.EmailAddress=LTRIM(RTRIM(o.Email)))",
                "NOT EXISTS(SELECT 1 FROM dbo.OrganizationAddresses a WHERE a.ShaleClientId=o.ShaleClientId AND a.OrganizationId=o.Id AND ISNULL(a.AddressLine1,N'''')",
                "NOT EXISTS(SELECT 1 FROM dbo.OrganizationWebsites w WHERE w.ShaleClientId=o.ShaleClientId AND w.OrganizationId=o.Id AND w.Website=LTRIM(RTRIM(o.Website)))"}) {
            assertTrue(sql.contains(guard), "historical identity guard is required");
            assertFalse(guard.contains("IsDeleted="), "deleted history must suppress rerun insertion");
        }
        assertFalse(sql.contains("UPDATE dbo.Organizations"));
        assertFalse(sql.contains("ALTER TABLE dbo.Organizations DROP"));
    }

    @Test void phoneBackfillRerunTreatsEveryHistoricalNonFaxKindAsTheLegacyVoiceIdentity() throws Exception {
        String sql = read("docs/sql/2026-09-10_organizations_phase3a_structured_contact_methods.sql");
        String guard = "NOT EXISTS(SELECT 1 FROM dbo.OrganizationPhoneNumbers p WHERE p.ShaleClientId=s.ShaleClientId AND p.OrganizationId=s.OrganizationId "
                + "AND ((s.Kind=N''FAX'' AND p.Kind=N''FAX'') OR (s.Kind=N''WORK'' AND p.Kind IN(N''MOBILE'',N''HOME'',N''WORK'',N''OTHER''))) "
                + "AND p.DisplayNumber=s.Value)";
        assertTrue(sql.contains(guard),
                "a historical exact MOBILE, HOME, WORK, or OTHER match must prevent rerun from manufacturing a WORK row, while Fax remains FAX-only");
        assertFalse(guard.contains("IsDeleted="),
                "a deleted historical voice match must continue suppressing rerun insertion");
    }

    @Test void verificationIsReadOnlyAndReportsZeroHealthyFindings() throws Exception {
        String verify = read("docs/sql/verification/2026-09-10_organizations_phase3a_structured_contact_methods_verification.sql");
        for (String finding : new String[]{"legacy Phone without historical structured voice match", "legacy Fax without historical fax match", "legacy Email without historical structured match", "populated partial address without historical structured match", "legacy Website without historical structured match", "active duplicates", "more than one active", "cross-tenant or orphan", "invalid sort orders", "blank active scalar values", "empty active addresses", "missing/wrong/unexpected RLS predicates", "untrusted, disabled, or cascading foreign keys"})
            assertTrue(verify.contains(finding), finding);
        assertTrue(verify.contains("@ExpectedDatabase sysname=N'REPLACE_WITH_APPROVED_DATABASE'")
                && verify.contains("@OperatorVerifiedAllTenantVisibility bit=0"),
                "read-only verification must also refuse execution until explicitly configured");
        assertTrue(verify.contains("ShaleClientId IN(7,8)") && verify.contains("missing context must return zero rows"));
        assertTrue(verify.contains("IF EXISTS(SELECT 1 FROM @Findings WHERE FindingCount<>0) THROW"));
        assertFalse(GO.matcher(verify).find());
        assertFalse(verify.matches("(?is).*\\b(?:ALTER|CREATE|DROP|UPDATE|DELETE|MERGE)\\s+(?:TABLE\\s+|INTO\\s+)?dbo\\..*"));
        assertFalse(verify.matches("(?is).*\\bINSERT\\s+(?:INTO\\s+)?dbo\\..*"));
        assertFalse(verify.matches("(?is).*COUNT_BIG\\(\\*\\)\\s+(?:AS\\s+)?RowCount\\b.*"));
    }

    @Test void verificationAcceptsExactHistoricalVoiceKindsButKeepsFaxKindSpecific() throws Exception {
        String verify = read("docs/sql/verification/2026-09-10_organizations_phase3a_structured_contact_methods_verification.sql");
        assertTrue(verify.contains("p.Kind IN(N''MOBILE'',N''HOME'',N''WORK'',N''OTHER'') AND p.DisplayNumber=LTRIM(RTRIM(o.Phone))"),
                "active or deleted MOBILE, HOME, WORK, and OTHER rows with the exact trimmed display value must satisfy legacy Phone verification");
        assertFalse(verify.contains("p.Kind IN(N''MOBILE'',N''HOME'',N''WORK'',N''FAX'',N''OTHER'')"),
                "FAX alone must not satisfy legacy Phone verification");
        assertTrue(verify.contains("p.Kind=N''FAX'' AND p.DisplayNumber=LTRIM(RTRIM(o.Fax))"),
                "legacy Fax must still require an exact historical FAX match");
    }

    @Test void runtimeOrganizationWritePathsRemainUnchangedByPhaseThreeA() throws Exception {
        String sql = read("docs/sql/2026-09-10_organizations_phase3a_structured_contact_methods.sql");
        assertFalse(sql.contains("CREATE TRIGGER"));
        assertFalse(sql.contains("EntityActionAuditLog"));
        assertTrue(Files.exists(repo("shale-data/src/main/java/com/shale/data/dao/OrganizationDao.java")));
        assertTrue(Files.exists(repo("shale-data/src/main/java/com/shale/data/dao/OrganizationTypeMutationDao.java")));
    }

    private static int count(String haystack, String needle) {
        int result = 0;
        for (int at = 0; (at = haystack.indexOf(needle, at)) >= 0; at += needle.length()) result++;
        return result;
    }
}
