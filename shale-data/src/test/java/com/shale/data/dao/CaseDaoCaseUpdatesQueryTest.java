package com.shale.data.dao;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

final class CaseDaoCaseUpdatesQueryTest {

    @Test
    void caseUpdatesQueryResolvesUsersDeletedColumnBeforeFilteringAuthors() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/shale/data/dao/CaseDao.java"));
        String method = source.substring(
                source.indexOf("private List<CaseUpdateDto> listCaseUpdatesInternal"),
                source.indexOf("public long addCaseTimelineEvent"));

        assertTrue(method.contains("resolveUsersDeletedColumn(con)"),
                "Case updates should resolve the available Users soft-delete column before adding an author filter");
        assertTrue(method.contains("u.ShaleClientId = cu.ShaleClientId"),
                "Case update authors should remain tenant-scoped");
        assertTrue(method.contains("AND ISNULL(cu.IsDeleted, 0) = 0"),
                "Case updates should continue filtering soft-deleted notes");
        assertTrue(method.contains("AND NULLIF(LTRIM(RTRIM(cu.NoteText)), '') IS NOT NULL"),
                "Case updates should continue excluding empty notes");
        assertFalse(method.contains("COALESCE(u.is_deleted, 0) = 0"),
                "Case updates must not hard-code Users.is_deleted because some deployments expose Users.IsDeleted instead");
    }
}
