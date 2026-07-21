package com.shale.data.dao;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

final class CaseLinksPhase541ContactReverseLookupTest {
    private static final Path CASE_DAO = Path.of("src/main/java/com/shale/data/dao/CaseDao.java");

    @Test
    void reverseLookupStartsFromActiveSharesAndBindsTenantThenContact() throws Exception {
        for (String body : reverseLookupBodies()) {
            String normalized = normalize(body);
            assertTrue(normalized.contains("FROM dbo.CaseLinkShares cls"));
            assertTrue(normalized.contains("WHERE cls.ShaleClientId = ? AND cls.ContactId = ? AND cls.IsDeleted = 0"));
            assertTrue(normalized.contains("ps.setInt(1, shaleClientId); ps.setInt(2, contactId);"));
        }
    }

    @Test
    void reverseLookupUsesPersistedShareContactAndTenantCompatibleActiveRows() throws Exception {
        for (String body : reverseLookupBodies()) {
            String normalized = normalize(body);
            assertTrue(normalized.contains("JOIN dbo.CaseLinks cl ON cl.Id = cls.CaseLinkId"));
            assertTrue(normalized.contains("AND cl.ShaleClientId = cls.ShaleClientId AND cl.IsDeleted = 0"));
            assertTrue(normalized.contains("JOIN dbo.ExternalLinks el ON el.Id = cl.ExternalLinkId"));
            assertTrue(normalized.contains("AND el.ShaleClientId = cl.ShaleClientId AND el.IsDeleted = 0"));
            assertTrue(normalized.contains("JOIN dbo.Contacts targetContact ON targetContact.Id = cls.ContactId"));
            assertTrue(normalized.contains("AND targetContact.ShaleClientId = cls.ShaleClientId"));
            assertTrue(normalized.contains("AND ISNULL(targetContact.IsDeleted, 0) = 0"));
            assertTrue(normalized.contains("AND (lt.ShaleClientId IS NULL OR lt.ShaleClientId = cls.ShaleClientId) AND lt.IsDeleted = 0"));
        }
    }

    @Test
    void reverseLookupHydratesSharesInBatchAndDoesNotDependOnCaseParties() throws Exception {
        for (String body : reverseLookupBodies()) {
            String normalized = normalize(body);
            assertTrue(normalized.contains("listCaseLinkSharesForLinks(con, shaleClientId, ids)"));
            assertFalse(normalized.contains("CaseParties"));
            assertFalse(normalized.contains("CaseContacts"));
        }
    }

    private static String[] reverseLookupBodies() throws Exception {
        String source = Files.readString(CASE_DAO);
        return new String[] { reverseLookupBody(source), reverseLookupBody(source.replace("\n", "\r\n")) };
    }

    private static String reverseLookupBody(String source) {
        int method = source.indexOf("public List<ContactSharedCaseLinkDto> listCaseLinksSharedWithContact");
        int open = source.indexOf('{', method);
        int depth = 0;
        for (int i = open; i < source.length(); i++) {
            char ch = source.charAt(i);
            if (ch == '{') depth++;
            if (ch == '}' && --depth == 0) return source.substring(method, i + 1);
        }
        throw new AssertionError("Could not extract reverse lookup method body");
    }

    private static String normalize(String value) {
        return value.replace("\r\n", "\n").replace('\t', ' ').replaceAll("\\s+", " ").trim();
    }
}
