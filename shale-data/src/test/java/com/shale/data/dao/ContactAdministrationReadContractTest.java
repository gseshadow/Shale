package com.shale.data.dao;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import org.junit.jupiter.api.Test;

class ContactAdministrationReadContractTest {
    @Test void administrationReadIsBoundedTenantScopedAuthorizedAndParameterized() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/shale/data/dao/ContactDao.java"));
        String method = source.substring(source.indexOf("listDefinitionsForAdministration("));
        assertTrue(method.contains("d.ShaleClientId IS NULL OR d.ShaleClientId=?"));
        assertTrue(method.contains("ISNULL(is_admin,0)=1"));
        assertTrue(method.contains("ISNULL(is_deleted,0)=0"));
        assertTrue(method.contains("ISNULL(IsRemoved,0)=0"));
        assertTrue(method.contains("ORDER BY d.SortOrder,d.Name,d.Id"));
        assertTrue(method.contains("rs.getBytes(\"RowVer\")"));
        assertFalse(method.contains("CaseParties"));
        assertFalse(method.contains("PartyRoles"));
        assertFalse(method.contains("CaseContacts"));
    }
}
