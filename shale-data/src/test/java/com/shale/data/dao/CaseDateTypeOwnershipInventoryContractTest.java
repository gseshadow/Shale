package com.shale.data.dao;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class CaseDateTypeOwnershipInventoryContractTest {
    private static final Path INVENTORY = Path.of("../docs/sql/verification/2026-08-10_case_date_type_ownership_inventory.sql");
    private static final Path MIGRATION = Path.of("../docs/sql/2026-08-10_case_date_type_tenant7_ownership.sql");

    @Test void inventoryIsReadOnlyAndCoversProductionOwnershipEvidence() throws Exception {
        String sql = Files.readString(INVENTORY);
        assertTrue(sql.contains("01_DEFINITIONS"));
        assertTrue(sql.contains("02_OCCURRENCE_USAGE"));
        assertTrue(sql.contains("03_FORM_REFERENCES"));
        assertTrue(sql.contains("04_REFERENCING_FOREIGN_KEYS"));
        assertTrue(sql.contains("05_SEMANTIC_ROLE_MAPPINGS"));
        assertTrue(sql.contains("06_CROSS_TENANT_BLOCKERS"));
        assertFalse(sql.matches("(?is).*\\bUPDATE\\s+dbo\\..*"));
        assertFalse(sql.matches("(?is).*\\bDELETE\\s+FROM\\s+dbo\\..*"));
        assertFalse(sql.matches("(?is).*\\bALTER\\s+(TABLE|SECURITY)\\b.*"));
    }

    @Test void inventoryNamesEveryNoncriticalCompatibilityKey() throws Exception {
        String sql = Files.readString(INVENTORY);
        for (String key : new String[]{"trial","hearing","mediation","deposition","discovery_deadline",
                "date_of_injury","date_of_medical_negligence","date_medical_negligence_discovered",
                "fee_agreement_signed","non_engagement_letter_sent"}) assertTrue(sql.contains("N'"+key+"'"), key);
    }

    @Test void ownershipCorrectionIsTransactionalIdempotentAndIdentityPreserving() throws Exception {
        String sql = Files.readString(MIGRATION);
        assertTrue(sql.contains("SET XACT_ABORT ON"));
        assertTrue(sql.contains("BEGIN TRANSACTION"));
        assertTrue(sql.contains("UPDLOCK,HOLDLOCK"));
        assertTrue(sql.contains("WHERE t.ShaleClientId IS NULL"));
        assertTrue(sql.contains("UPDATE t SET ShaleClientId=7"));
        assertTrue(sql.contains("@@ROWCOUNT NOT IN(0,10)"));
        assertFalse(sql.matches("(?is).*UPDATE\s+(dbo\.)?(CaseDates|FormConfiguredFields|CaseDateTypeSemanticRoleMappings).*"));
        assertFalse(sql.matches("(?is).*INSERT\s+(INTO\s+)?dbo\.(CaseDateTypes|CaseDates|FormConfiguredFields).*"));
        assertTrue(sql.contains("@OccurrenceCount"));
        assertTrue(sql.contains("@FormCount"));
        assertTrue(sql.contains("fk.referenced_object_id=OBJECT_ID(N'dbo.CaseDateTypes')"));
        assertTrue(sql.contains("COUNT_BIG(*) FROM dbo.CaseDateTypes WHERE ShaleClientId IS NULL)<>3"));
    }

    @Test void ownershipCorrectionFreezesAuthoritativeProductionIdentity() throws Exception {
        String sql = Files.readString(MIGRATION);
        for (String identity : new String[]{"(7,N'trial',N'Trial')","(8,N'hearing',N'Hearing')",
                "(9,N'mediation',N'Mediation')","(10,N'deposition',N'Deposition')",
                "(3,N'discovery_deadline',N'Discovery Deadline')","(4,N'date_of_injury',N'Date of Injury')",
                "(5,N'date_of_medical_negligence',N'Date of Medical Negligence')",
                "(6,N'date_medical_negligence_discovered',N'Date Medical Negligence Was Discovered')",
                "(12,N'fee_agreement_signed',N'Fee Agreement Signed')",
                "(13,N'non_engagement_letter_sent',N'Non-Engagement Letter Sent')"}) assertTrue(sql.contains(identity), identity);
        for (String blocker : new String[]{"missing or ambiguous","identity or lifecycle","conflicting ownership",
                "identity conflicts","semantic-role participation","Cross-tenant Case Date","Cross-tenant form",
                "unreviewed Case Date Type foreign-key consumer","personal test tenant"}) assertTrue(sql.contains(blocker), blocker);
    }
}
