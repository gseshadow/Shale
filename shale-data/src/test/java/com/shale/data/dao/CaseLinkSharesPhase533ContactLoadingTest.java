package com.shale.data.dao;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

final class CaseLinkSharesPhase533ContactLoadingTest {
    private static final Path CASE_DAO = Path.of("src/main/java/com/shale/data/dao/CaseDao.java");

    @Test
    void contactOptionQueriesUseVerifiedContactColumnsAndDisplayFallback() throws Exception {
        String source = Files.readString(CASE_DAO);
        String region = source.substring(source.indexOf("public List<CaseLinkContactOptionDto> searchCaseLinkShareContacts"),
                source.indexOf("public List<CaseLinkShareDto> listCaseLinkShares"));

        assertFalse(Pattern.compile("ct\\.Email(?!Personal|Work|Other)").matcher(region).find(), "generic dbo.Contacts.Email must not be referenced");
        assertTrue(region.contains("ct.EmailPersonal"));
        assertTrue(region.contains("ct.EmailWork"));
        assertTrue(region.contains("ct.EmailOther"));
        assertTrue(region.contains("COALESCE(NULLIF(LTRIM(RTRIM("));
        assertTrue(region.contains(".Name"));
        assertTrue(region.contains("CONCAT("));
        assertTrue(region.contains(".FirstName"));
        assertTrue(region.contains(".LastName"));
        assertTrue(region.contains(".WorkName"));
        assertTrue(region.contains("ct.ShaleClientId = ?"));
        assertTrue(region.contains("ISNULL(ct.IsDeleted, 0) = 0"));
        assertTrue(region.contains("ORDER BY DisplayName ASC, ct.Id ASC"));
    }

    @Test
    void completeContactOptionOperationIsNotCappedAtOneHundred() throws Exception {
        String source = Files.readString(CASE_DAO);
        String listMethod = source.substring(source.indexOf("public List<CaseLinkContactOptionDto> listCaseLinkShareContacts"),
                source.indexOf("public List<CaseLinkContactOptionDto> listCaseLinkShareCaseContacts"));

        assertFalse(Pattern.compile("TOP\\s*\\(").matcher(listMethod).find(), "complete list must not use TOP");
        assertFalse(listMethod.contains("100"), "complete list must not use a 100-row magic cap");
        assertTrue(listMethod.contains("ct.ShaleClientId = ?"));
        assertTrue(listMethod.contains("ISNULL(ct.IsDeleted, 0) = 0"));
    }

    @Test
    void caseContactsUseAuthoritativeAssociationAndTenantValidation() throws Exception {
        String source = Files.readString(CASE_DAO);
        String method = source.substring(source.indexOf("public List<CaseLinkContactOptionDto> listCaseLinkShareCaseContacts"),
                source.indexOf("private static String caseLinkShareContactDisplayNameExpression"));

        assertTrue(method.contains("FROM dbo.CaseParties cp"));
        assertFalse(method.contains("dbo.CaseContacts"));
        assertTrue(method.contains("JOIN dbo.Contacts ct ON ct.Id = cp.ContactId"));
        assertTrue(method.contains("cp.ContactId IS NOT NULL"));
        assertTrue(method.contains("c.ShaleClientId = ?"));
        assertTrue(method.contains("ct.ShaleClientId = ?"));
        assertTrue(method.contains("ISNULL(ct.IsDeleted, 0) = 0"));
        assertTrue(method.contains("GROUP BY ct.Id"));
        assertTrue(method.contains("ORDER BY DisplayName ASC, ct.Id ASC"));
    }
}
