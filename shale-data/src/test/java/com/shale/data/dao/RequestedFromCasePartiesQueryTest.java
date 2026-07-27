package com.shale.data.dao;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

final class RequestedFromCasePartiesQueryTest {
    @Test void queryUsesAuthoritativeCasePartiesWithTenantEligibilityAndIdDeduplication() throws Exception {
        String source=Files.readString(Path.of("src/main/java/com/shale/data/dao/CaseDao.java"));
        String method=source.substring(source.indexOf("public List<CasePartyEntityOptionDto> listRequestedFromCaseParties"),source.indexOf("private static String caseLinkShareContactDisplayNameExpression"));
        assertTrue(method.contains("FROM dbo.CaseParties"));
        assertTrue(method.contains("JOIN dbo.Contacts"));
        assertTrue(method.contains("JOIN dbo.Organizations"));
        assertTrue(method.contains("c.ShaleClientId=?"));
        assertTrue(method.contains("ct.ShaleClientId=?"));
        assertTrue(method.contains("org.ShaleClientId=?"));
        assertTrue(method.contains("ISNULL(ct.IsDeleted,0)=0"));
        assertTrue(method.contains("ISNULL(org.IsDeleted,0)=0"));
        assertTrue(method.contains("GROUP BY EntityType, EntityId"));
        assertTrue(method.contains("ORDER BY EntityType, DisplayName, EntityId"));
        assertTrue(method.contains("caseLinkShareContactDisplayNameExpression"));
    }
}
