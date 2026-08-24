package com.shale.data.dao;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class ContactFoundationMigrationContractTest {
    private static Path repo(String path) { Path p=Path.of(path); return Files.exists(p)?p:Path.of("..").resolve(path); }
    private static String read(String path) throws Exception { return Files.readString(repo(path)); }

    @Test void migrationIsAdditiveTenantSafeAndBackfillsExpert() throws Exception {
        String s=read("docs/sql/2026-08-24_contacts_foundation_phase1a.sql");
        for(String table:new String[]{"ContactTypes","Specialties","CredentialDefinitions","ContactContactTypes","ContactSpecialties","ContactCredentials"})
            assertTrue(s.contains("CREATE TABLE dbo."+table),table);
        for(String column:new String[]{"Prefix","MiddleName","PreferredName","Suffix"}) assertTrue(s.contains("Contacts ADD "+column));
        assertTrue(s.contains("FOREIGN KEY(ShaleClientId,ContactId) REFERENCES dbo.Contacts(ShaleClientId,Id)"));
        assertTrue(s.contains("(N'ContactTypes',N'fn_FilterByTenantOrGlobal')"));
        assertTrue(s.contains("(N'ContactCredentials',N'fn_FilterByTenant')"));
        assertTrue(s.contains("ADD FILTER PREDICATE sec."));
        assertTrue(s.contains("WHERE c.IsExpert=1 AND NOT EXISTS"));
        assertTrue(s.contains("WHERE ShaleClientId IS NULL AND SystemKey=N'expert'"));
        assertFalse(s.contains("UPDATE dbo.Contacts"));
        assertFalse(s.contains("DROP COLUMN"));
        assertFalse(s.contains("CREATE SECURITY POLICY"));
    }

    @Test void verificationAndArchitectureCoverDeferredSafetyRules() throws Exception {
        String v=read("docs/sql/verification/2026-08-24_contacts_foundation_phase1a_verification.sql");
        String a=read("architecture/contact-management.md");
        for(String phrase:new String[]{"missing expert assignments","duplicate active credentials","cross-tenant definition assignment","invalid assignment deletion metadata","sys.security_predicates"}) assertTrue(v.contains(phrase));
        assertTrue(a.contains("dual-write"));
        assertTrue(a.contains("CaseParties` plus `PartyRoles"));
        assertTrue(a.contains("has **not** been\nexecuted"));
    }
}
