package com.shale.data.dao;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

final class CaseLinksPhase541ContactReverseLookupTest {
    private static final Path CASE_DAO = Path.of("src/main/java/com/shale/data/dao/CaseDao.java");

    @Test
    void reverseLookupStartsFromActiveSharesAndBindsTenantThenContact() throws Exception {
        String source = Files.readString(CASE_DAO);
        int method = source.indexOf("public List<ContactSharedCaseLinkDto> listCaseLinksSharedWithContact");
        String body = source.substring(method, source.indexOf("public List<CaseLinkShareDto> listCaseLinkShares", method));

        assertTrue(body.contains("FROM dbo.CaseLinkShares cls"));
        assertTrue(body.contains("WHERE cls.ShaleClientId = ?\n\t\t\t\t\t  AND cls.ContactId = ?\n\t\t\t\t\t  AND cls.IsDeleted = 0"));
        assertTrue(body.contains("ps.setInt(1, shaleClientId);\n\t\t\t\tps.setInt(2, contactId);"));
    }

    @Test
    void reverseLookupUsesPersistedShareContactAndTenantCompatibleActiveRows() throws Exception {
        String source = Files.readString(CASE_DAO);
        int method = source.indexOf("public List<ContactSharedCaseLinkDto> listCaseLinksSharedWithContact");
        String body = source.substring(method, source.indexOf("public List<CaseLinkShareDto> listCaseLinkShares", method));

        assertTrue(body.contains("JOIN dbo.CaseLinks cl ON cl.Id = cls.CaseLinkId"));
        assertTrue(body.contains("AND cl.ShaleClientId = cls.ShaleClientId\n\t\t\t\t\t AND cl.IsDeleted = 0"));
        assertTrue(body.contains("JOIN dbo.ExternalLinks el ON el.Id = cl.ExternalLinkId"));
        assertTrue(body.contains("AND el.ShaleClientId = cl.ShaleClientId\n\t\t\t\t\t AND el.IsDeleted = 0"));
        assertTrue(body.contains("JOIN dbo.Contacts targetContact ON targetContact.Id = cls.ContactId"));
        assertTrue(body.contains("AND targetContact.ShaleClientId = cls.ShaleClientId"));
        assertTrue(body.contains("AND ISNULL(targetContact.IsDeleted, 0) = 0"));
        assertTrue(body.contains("AND (lt.ShaleClientId IS NULL OR lt.ShaleClientId = cls.ShaleClientId)\n\t\t\t\t\t AND lt.IsDeleted = 0"));
    }

    @Test
    void reverseLookupHydratesSharesInBatchAndDoesNotDependOnCaseParties() throws Exception {
        String source = Files.readString(CASE_DAO);
        int method = source.indexOf("public List<ContactSharedCaseLinkDto> listCaseLinksSharedWithContact");
        String body = source.substring(method, source.indexOf("public List<CaseLinkShareDto> listCaseLinkShares", method));

        assertTrue(body.contains("listCaseLinkSharesForLinks(con, shaleClientId, ids)"));
        assertTrue(!body.contains("CaseParties"));
        assertTrue(!body.contains("CaseContacts"));
    }
}
