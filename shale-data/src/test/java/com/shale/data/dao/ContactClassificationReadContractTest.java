package com.shale.data.dao;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class ContactClassificationReadContractTest {
    private static String source() throws Exception {
        return Files.readString(Path.of("src/main/java/com/shale/data/dao/ContactDao.java"));
    }

    @Test
    void effectiveReadsImplementOverlayFallbackIsolationAndStableOrdering() throws Exception {
        String source = source();
        assertTrue(source.contains("PARTITION BY d.SystemKey"));
        assertTrue(source.contains("CASE WHEN d.ShaleClientId=? THEN 0 ELSE 1 END,d.Id"));
        assertTrue(source.contains("d.IsDeleted=0")); // deleted overrides reset, allowing global fallback
        assertTrue(source.contains("WHERE rn=1 AND IsActive=1")); // inactive override masks global
        assertTrue(source.contains("ORDER BY SortOrder,Name,Id"));
        assertTrue(source.contains("verifyTenantMatchesSession(con, shaleClientId)"));
    }

    @Test
    void profileIsOneAggregateLoadWithExactIdsHistoricalRowsAndRequiredOrdering() throws Exception {
        String source = source();
        assertTrue(source.contains("c.Id=? AND c.ShaleClientId=?"));
        assertTrue(source.contains("a.ContactId=? AND a.ShaleClientId=? AND a.IsDeleted=0"));
        assertTrue(source.contains("d.Id=a.CredentialDefinitionId"));
        assertTrue(source.contains("ORDER BY a.DisplayOrder,d.SortOrder,d.Name,d.Id"));
        assertTrue(source.contains("!definition.active() || definition.deleted()"));
        assertFalse(source.contains("ContactCredentials a JOIN dbo.CredentialDefinitions d\n                  ON d.SystemKey"));
    }

    @Test
    void compatibilityBoundariesRemainReadOnlyAndLegacyAuthoritative() throws Exception {
        String source = source();
        assertTrue(source.contains("LegacyDisplayName"));
        assertTrue(source.contains("c.Prefix,c.FirstName,c.MiddleName,c.LastName,c.PreferredName,c.Suffix"));
        assertFalse(source.contains("UPDATE dbo.ContactContactTypes"));
        assertFalse(source.contains("INSERT dbo.ContactContactTypes"));
        assertFalse(source.contains("PartyRoles"));
        assertFalse(source.contains("CaseParties"));
        assertFalse(source.contains("CaseContacts"));
    }
}
