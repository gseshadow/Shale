package com.shale.data.dao;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class CaseDateTypeOwnershipInventoryContractTest {
    private static final Path INVENTORY = Path.of("../docs/sql/verification/2026-08-10_case_date_type_ownership_inventory.sql");

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
}
