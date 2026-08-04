package com.shale.data.dao;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class CaseDateDeletedReadContractTest {
    @Test void deletedOccurrenceReadIsTenantCaseScopedAndReadOnly() throws Exception {
        String dao = Files.readString(Path.of("src/main/java/com/shale/data/dao/CaseDateDao.java"));
        assertTrue(dao.contains("listDeletedCaseDatesForCase(long caseId, int tenant, int actor)"));
        assertTrue(dao.contains("validateActor(con, tenant, actor); validateCase(con, tenant, caseId);"));
        assertTrue(dao.contains("cd.CaseId = ? AND cd.ShaleClientId = ? AND cd.IsDeleted = 1"));
        assertTrue(dao.contains("COALESCE(eff.Name, st.Name) AS TypeName"));
        assertFalse(dao.contains("DELETE FROM dbo.CaseDates"));
    }
}
