package com.shale.data.dao;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

class ContactPhase2CAContactPointsMigrationContractTest {
    private static final Pattern GO = Pattern.compile("(?im)^\\s*GO\\s*$");
    private static Path repo(String path) {
        Path direct = Path.of(path);
        return Files.exists(direct) ? direct : Path.of("..").resolve(path);
    }
    private static String read(String path) throws Exception {
        return Files.readString(repo(path)).replace("\r\n", "\n").replace('\r', '\n');
    }

    @Test void guardAndTransactionWrapTheOnlyMutationBatch() throws Exception {
        String sql = read("docs/sql/2026-08-26_contacts_phase2c_a_contact_points.sql");
        int guard = sql.indexOf("DECLARE @OperatorVerifiedAllTenantVisibility bit=0");
        int context = sql.indexOf("SESSION_CONTEXT(N'ShaleClientId') IS NOT NULL");
        int principal = sql.indexOf("USER_NAME() IN(N'shale_app',N'shale_runtime')");
        int tx = sql.indexOf("BEGIN TRANSACTION;");
        int ddl = sql.indexOf("CREATE TABLE dbo.ContactPhoneNumbers");
        int commit = sql.indexOf("COMMIT TRANSACTION;");
        int rollback = sql.indexOf("IF @@TRANCOUNT>0 ROLLBACK TRANSACTION; THROW;");
        assertTrue(guard < context && context < principal && principal < tx && tx < ddl && ddl < commit && commit < rollback);
        assertTrue(sql.contains("SET XACT_ABORT ON;"));
        assertEquals(1, GO.matcher(sql).results().count());
        assertTrue(GO.matcher(sql).find());
    }

    @Test void schemaUsesTenantSafeOwnershipActorsAndNoCascade() throws Exception {
        String sql = read("docs/sql/2026-08-26_contacts_phase2c_a_contact_points.sql");
        for (String table : new String[]{"ContactPhoneNumbers", "ContactEmailAddresses", "ContactAddresses"}) {
            assertTrue(sql.contains("CREATE TABLE dbo." + table));
            assertTrue(sql.contains("N'FK_'+t+N'_Contact_Tenant'"));
            assertTrue(sql.contains("N'" + table + "'"));
        }
        for (String actor : new String[]{"CreatedByUserId", "UpdatedByUserId", "DeletedByUserId"})
            assertTrue(sql.contains(actor));
        assertTrue(sql.contains("REFERENCES dbo.Users(id)"));
        assertTrue(sql.contains("f.delete_referential_action<>0"));
        assertTrue(sql.contains("REFERENCES dbo.'+QUOTENAME(@Parent)"));
        assertFalse(sql.contains("ON DELETE CASCADE"));
    }

    @Test void closedKindsOrderingLifecycleAndPrimaryUniquenessAreEnforced() throws Exception {
        String sql = read("docs/sql/2026-08-26_contacts_phase2c_a_contact_points.sql");
        assertTrue(sql.contains("Kind IN(N'MOBILE',N'HOME',N'WORK',N'FAX',N'OTHER')"));
        assertTrue(sql.contains("Kind IN(N'PERSONAL',N'WORK',N'OTHER')"));
        assertTrue(sql.contains("Kind IN(N'HOME',N'WORK',N'OTHER')"));
        assertEquals(3, count(sql, "CHECK(SortOrder>=0)"));
        assertEquals(3, count(sql, "WHERE IsDeleted=0 AND IsPrimary=1;"));
        assertEquals(3, count(sql, "IsDeleted=1 AND IsPrimary=0 AND DeletedAt IS NOT NULL AND DeletedByUserId IS NOT NULL"));
    }

    @Test void rlsIsStrictAndContactsIsDeliberatelyUnchanged() throws Exception {
        String sql = read("docs/sql/2026-08-26_contacts_phase2c_a_contact_points.sql");
        assertTrue(sql.contains("ADD FILTER PREDICATE sec.fn_FilterByTenant(ShaleClientId)"));
        assertTrue(sql.contains("exactly the strict tenant FILTER predicate"));
        assertTrue(sql.contains("target_object_id=OBJECT_ID(N'dbo.Contacts')"));
        assertFalse(sql.contains("ON dbo.Contacts ADD FILTER"));
        assertFalse(sql.contains("fn_FilterByTenantOrGlobal"));
    }

    @Test void backfillIsLosslessIdempotentAndIncludesDeletedContacts() throws Exception {
        String sql = read("docs/sql/2026-08-26_contacts_phase2c_a_contact_points.sql");
        for (String legacy : new String[]{"PhoneCell", "PhoneHome", "PhoneWork", "EmailPersonal", "EmailWork", "EmailOther", "AddressHome", "AddressWork", "AddressOther"})
            assertTrue(sql.contains("c." + legacy));
        assertFalse(sql.contains("FROM dbo.Contacts c WHERE c.IsDeleted=0"));
        assertTrue(sql.contains("DisplayNumber,NormalizedNumber"));
        assertTrue(sql.contains("EmailAddress,NormalizedEmail"));
        assertTrue(sql.contains("Kind,LegacyAddressText,IsPrimary"));
        assertTrue(sql.contains("p.Kind=s.Kind AND p.DisplayNumber=s.Value);"));
        assertTrue(sql.contains("e.Kind=s.Kind AND e.EmailAddress=s.Value);"));
        assertTrue(sql.contains("a.Kind=s.Kind AND a.LegacyAddressText=s.Value);"));
        assertFalse(sql.contains("UPDATE dbo.Contacts"));
        assertFalse(sql.contains("ALTER TABLE dbo.Contacts DROP"));
    }

    @Test void normalizationIsConservativeAndAddressesAreNeverParsed() throws Exception {
        String sql = read("docs/sql/2026-08-26_contacts_phase2c_a_contact_points.sql");
        assertTrue(sql.contains("s.Value LIKE N'+%'"));
        assertTrue(sql.contains("THEN LOWER(s.Value) END"));
        assertTrue(sql.contains("INSERT dbo.ContactAddresses(ShaleClientId,ContactId,Kind,LegacyAddressText,IsPrimary,SortOrder)"));
        assertFalse(sql.contains("INSERT dbo.ContactAddresses(ShaleClientId,ContactId,Kind,AddressLine1"));
    }

    @Test void verificationIsReadOnlyAggregateAndPhiSafe() throws Exception {
        String verify = read("docs/sql/verification/2026-08-26_contacts_phase2c_a_verification.sql");
        for (String forbidden : new String[]{"INSERT ", "UPDATE ", "DELETE ", "ALTER ", "CREATE ", "DROP ", "FirstName", "LastName", "Contacts.Name", "SELECT Phone", "SELECT Email", "SELECT Address"})
            assertFalse(verify.contains(forbidden), forbidden);
        for (String required : new String[]{"source legacy population", "missing backfills", "duplicate backfills", "cross-tenant/orphan", "invalid lifecycle", "duplicate active primaries", "sys.security_predicates"})
            assertTrue(verify.contains(required), required);
        assertTrue(verify.trim().endsWith("END TRY BEGIN CATCH THROW; END CATCH;"));
        assertFalse(GO.matcher(verify).find());
    }

    @Test void caseRolesRuntimeAndPresentationRemainOutsideTheMigration() throws Exception {
        String sql = read("docs/sql/2026-08-26_contacts_phase2c_a_contact_points.sql");
        for (String table : new String[]{"CaseParties", "PartyRoles", "CaseContacts"}) assertFalse(sql.contains(table));
        assertFalse(sql.contains("IsExpert"));
        assertTrue(Files.exists(repo("shale-data/src/main/java/com/shale/data/dao/ContactDao.java")));
        assertTrue(Files.exists(repo("shale-ui/src/main/resources")));
    }

    private static int count(String haystack, String needle) {
        int result = 0;
        for (int at = 0; (at = haystack.indexOf(needle, at)) >= 0; at += needle.length()) result++;
        return result;
    }
}
