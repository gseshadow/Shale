package com.shale.data.dao;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

final class CaseDaoRelatedContactsQueryTest {

    @Test
    void caseDetailRelatedContactsUseCasePartiesContactPath() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/shale/data/dao/CaseDao.java"));
        String method = source.substring(
                source.indexOf("private static String relatedCasePartyContactsSql"),
                source.indexOf("public List<RelatedContactRow> findRelatedContacts"));

        assertTrue(method.contains("FROM dbo.CaseParties cp"),
                "Case detail related contacts should come from the case-party contact relationship used by the desktop app");
        assertTrue(method.contains("INNER JOIN dbo.PartyRoles pr"));
        assertTrue(method.contains("INNER JOIN dbo.Contacts ct"));
        assertTrue(method.contains("ct.ShaleClientId = ?"));
        assertTrue(method.contains("AND (ct.IsDeleted = 0 OR ct.IsDeleted IS NULL)"));
        assertFalse(method.contains("FROM dbo.CaseContacts cc"),
                "CaseContacts is not the populated source for the tested case detail parties");
    }
}
