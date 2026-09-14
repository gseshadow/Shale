package com.shale.data.dao;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

class ContactClassificationReadContractTest {
    private static String source() throws Exception {
        return Files.readString(Path.of("src/main/java/com/shale/data/dao/ContactDao.java"))
                .replace("\r\n", "\n").replace('\r', '\n');
    }

    private static String methodBody(String source, String signature) {
        int signatureStart = source.indexOf(signature);
        assertTrue(signatureStart >= 0, "method signature not found: " + signature);
        int openingBrace = source.indexOf('{', signatureStart + signature.length());
        assertTrue(openingBrace >= 0, "opening brace not found: " + signature);
        int depth = 0;
        for (int index = openingBrace; index < source.length(); index++) {
            char current = source.charAt(index);
            if (current == '{') depth++;
            if (current == '}' && --depth == 0) return source.substring(openingBrace, index + 1);
        }
        throw new AssertionError("unbalanced method body: " + signature);
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
        String phaseOneBReads = String.join("\n",
                methodBody(source, "public List<DefinitionRow> listEffectiveDefinitions("),
                methodBody(source, "public List<CredentialDefinitionRow> listEffectiveCredentialDefinitions("),
                methodBody(source, "public ClassificationProfileRow findClassificationProfile("),
                methodBody(source, "private static List<AssignedDefinitionRow> loadAssignedDefinitions("),
                methodBody(source, "private static List<AssignedCredentialRow> loadAssignedCredentials("));

        assertTrue(phaseOneBReads.contains("LegacyDisplayName"));
        assertTrue(phaseOneBReads.contains("c.Prefix,c.FirstName,c.MiddleName,c.LastName,c.PreferredName,c.Suffix"));
        assertTrue(phaseOneBReads.contains("SELECT ") || phaseOneBReads.contains("WITH visible AS"));
        assertTrue(phaseOneBReads.contains("executeQuery()"));
        assertFalse(phaseOneBReads.contains("executeUpdate"));
        assertFalse(Pattern.compile("(?i)\\b(?:INSERT|UPDATE|DELETE|MERGE)\\s+(?:INTO\\s+)?dbo\\.")
                .matcher(phaseOneBReads).find(), "Phase 1B methods must contain no mutation SQL");
        assertFalse(phaseOneBReads.contains("PartyRoles"));
        assertFalse(phaseOneBReads.contains("CaseParties"));
        assertFalse(phaseOneBReads.contains("CaseContacts"));
    }
}
