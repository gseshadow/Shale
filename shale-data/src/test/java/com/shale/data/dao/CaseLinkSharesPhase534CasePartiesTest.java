package com.shale.data.dao;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

final class CaseLinkSharesPhase534CasePartiesTest {
    private static final Path CASE_DAO = Path.of("src/main/java/com/shale/data/dao/CaseDao.java");

    @Test
    void caseContactSuggestionsUseCasePartiesNotLegacyCaseContacts() throws Exception {
        String method = methodSource();
        assertTrue(method.contains("FROM dbo.CaseParties cp"));
        assertFalse(method.contains("dbo.CaseContacts"));
        assertTrue(method.contains("JOIN dbo.Cases c ON c.Id = cp.CaseId"));
        assertTrue(method.contains("JOIN dbo.Contacts ct ON ct.Id = cp.ContactId"));
        assertTrue(method.contains("cp.CaseId = ?"));
    }

    @Test
    void caseContactSuggestionsRequireContactBackedActiveTenantRows() throws Exception {
        String method = methodSource();
        assertTrue(method.contains("cp.ContactId IS NOT NULL"));
        assertTrue(method.contains("c.ShaleClientId = ?"));
        assertTrue(method.contains("ct.ShaleClientId = ?"));
        assertTrue(method.contains("ISNULL(c.IsDeleted, 0) = 0"));
        assertTrue(method.contains("ISNULL(ct.IsDeleted, 0) = 0"));
        assertTrue(method.contains("GROUP BY ct.Id"));
        assertTrue(method.contains("ORDER BY DisplayName ASC, ct.Id ASC"));
    }

    @Test
    void caseContactSuggestionsUseAllContactsDisplayFallback() throws Exception {
        String source = Files.readString(CASE_DAO);
        String method = methodSource();
        String expressionName = "caseLinkShareContactDisplayNameExpression(\"ct\")";
        assertTrue(method.contains(expressionName));
        assertTrue(source.contains("public List<CaseLinkContactOptionDto> listCaseLinkShareContacts"));
        assertTrue(source.contains("COALESCE(NULLIF(LTRIM(RTRIM("));
        assertTrue(method.contains("AND %s IS NOT NULL"));
    }

    private static String methodSource() throws Exception {
        String source = Files.readString(CASE_DAO);
        return source.substring(source.indexOf("public List<CaseLinkContactOptionDto> listCaseLinkShareCaseContacts"),
                source.indexOf("private static String caseLinkShareContactDisplayNameExpression"));
    }
}
