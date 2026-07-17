package com.shale.data.dao;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

final class CaseLinksPhase542ContactReverseLookupLifecycleTest {
    private static final Path CASE_DAO = Path.of("src/main/java/com/shale/data/dao/CaseDao.java");

    @Test
    void reverseLookupKeepsCaseTenantPredicateButDoesNotFilterCaseSoftDelete() throws Exception {
        String body = reverseLookupBody();

        assertTrue(body.contains("JOIN dbo.Cases c ON c.Id = cl.CaseId\n\t\t\t\t\t AND c.ShaleClientId = cls.ShaleClientId"));
        assertFalse(body.contains("c.IsDeleted = 0"), "Cases.IsDeleted must not erase an active persisted share from Contact View.");
        assertFalse(body.contains("ISNULL(c.IsDeleted"), "The reverse lookup intentionally uses Case identity and tenant compatibility, not Case soft-delete state.");
    }

    @Test
    void reverseLookupStillExcludesDeletedShareLinkExternalLinkContactAndLinkTypeRows() throws Exception {
        String body = reverseLookupBody();

        assertTrue(body.contains("AND cls.IsDeleted = 0"));
        assertTrue(body.contains("AND cl.IsDeleted = 0"));
        assertTrue(body.contains("AND el.IsDeleted = 0"));
        assertTrue(body.contains("AND ISNULL(targetContact.IsDeleted, 0) = 0"));
        assertTrue(body.contains("AND lt.IsDeleted = 0"));
        assertTrue(body.contains("AND (lt.ShaleClientId IS NULL OR lt.ShaleClientId = cls.ShaleClientId)"));
    }

    @Test
    void reverseLookupHasBoundedZeroResultJoinStageDiagnostics() throws Exception {
        String body = reverseLookupBody();
        String source = Files.readString(CASE_DAO);

        assertTrue(body.contains("if (links.isEmpty()) logContactSharedLinkJoinStages(con, shaleClientId, contactId, 0);"));
        assertTrue(source.contains("activeShareCount="));
        assertTrue(source.contains("caseLinkExternalLinkJoinCount="));
        assertTrue(source.contains("finalReturnedCount="));
        assertFalse(source.contains("el.Url AS"));
    }

    private static String reverseLookupBody() throws Exception {
        String source = Files.readString(CASE_DAO);
        int method = source.indexOf("public List<ContactSharedCaseLinkDto> listCaseLinksSharedWithContact");
        return source.substring(method, source.indexOf("public List<CaseLinkShareDto> listCaseLinkShares", method));
    }
}
