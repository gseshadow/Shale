package com.shale.data.dao;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ContactDirectoryPhase2CDContractTest {
    private static String source() throws Exception {
        return Files.readString(Path.of("src/main/java/com/shale/data/dao/ContactDao.java"));
    }

    @Test void cardsUseBoundedStructuredPrimaryProjectionsWithoutLegacyFallback() throws Exception {
        String s=source(), m=s.substring(s.indexOf("public PagedResult<ContactCardSummaryRow> findDirectoryContactsPage"),s.indexOf("public long countDirectoryContacts"));
        assertTrue(m.contains("ContactPhoneNumbers")); assertTrue(m.contains("ContactEmailAddresses"));
        assertTrue(m.contains("IsPrimary DESC,e.SortOrder,e.Id")); assertTrue(m.contains("IsPrimary DESC,p.SortOrder,p.Id"));
        assertFalse(m.contains("schema.emailColumn()")); assertFalse(m.contains("schema.phoneColumn()"));
        assertTrue(m.contains("OFFSET ? ROWS FETCH NEXT ? ROWS ONLY"));
    }

    @Test void searchCoversStructuredNamesClassificationsAndContactPoints() throws Exception {
        String s=source(), p=s.substring(s.indexOf("private static String structuredDirectoryPredicate"),s.indexOf("private static int bindStructuredDirectoryQuery"));
        assertTrue(p.contains("lightweightDisplayNameExpression(schema, \"c\")"));
        assertFalse(p.contains("COALESCE(c.DisplayName"), "search must not use the former invalid DisplayName alias");
        for(String value:new String[]{"Prefix","FirstName","MiddleName","LastName","PreferredName","Suffix","ContactContactTypes","ContactSpecialties","ContactCredentials","CredentialDefinitions","DisplayNumber","NormalizedNumber","EmailAddress","NormalizedEmail","AddressLine1","AddressLine2","City","StateOrProvince","PostalCode","CountryCode","LegacyAddressText"}) assertTrue(p.contains(value),value);
        assertTrue(p.contains("IsDeleted=0")); assertTrue(p.contains(" IN (")); assertTrue(p.contains("ContactTypeId")); assertTrue(p.contains("SpecialtyId")); assertTrue(p.contains("CredentialDefinitionId"));
        for(String legacyScalar:new String[]{"c.PhoneCell","c.PhoneHome","c.PhoneWork","c.EmailPersonal","c.EmailWork","c.EmailOther","c.AddressHome","c.AddressWork","c.AddressOther"}) assertFalse(p.contains(legacyScalar),legacyScalar);
    }

    @Test void wildcardEscapingIsExplicit() throws Exception {
        assertEquals("a\\%b\\_c\\[d", ContactDao.escapeLike("a%b_c[d"));
    }
}
