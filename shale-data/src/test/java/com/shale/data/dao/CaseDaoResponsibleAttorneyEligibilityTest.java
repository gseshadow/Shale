package com.shale.data.dao;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import com.shale.core.semantics.RoleSemantics;

class CaseDaoResponsibleAttorneyEligibilityTest {
    private static final Path SOURCE = Path.of("src/main/java/com/shale/data/dao/CaseDao.java");

    @Test
    void responsibleAttorneyCandidatesUseAuthoritativeAttorneyFlagAndTenantBoundary() throws Exception {
        String method = method(Files.readString(SOURCE), "public List<UserRow> listAttorneysForTenant");

        assertEquals("is_attorney", RoleSemantics.FLAG_IS_ATTORNEY);
        assertTrue(method.contains("WHERE u.ShaleClientId = ?"));
        assertTrue(method.contains("AND COALESCE(u.%s, 0) = 1"));
        assertTrue(method.contains(".formatted(RoleSemantics.FLAG_IS_ATTORNEY)"));
        assertEquals(1, occurrences(method, "ps.setInt(1, shaleClientId)"));
    }

    @Test
    void responsibleAttorneyCandidatesPreserveActiveDeletedRulesAndStableIdentityOrdering() throws Exception {
        String method = method(Files.readString(SOURCE), "public List<UserRow> listAttorneysForTenant");

        assertTrue(method.contains("tableHasColumn(con, \"Users\", \"IsActive\")"));
        assertTrue(method.contains("tableHasColumn(con, \"Users\", \"IsDeleted\")"));
        assertTrue(method.contains("tableHasColumn(con, \"Users\", \"is_deleted\")"));
        assertTrue(method.contains("u.IsActive = 1 OR u.IsActive IS NULL"));
        assertTrue(method.contains("u.IsDeleted = 0 OR u.IsDeleted IS NULL"));
        assertTrue(method.contains("u.is_deleted = 0 OR u.is_deleted IS NULL"));
        assertTrue(method.contains("ORDER BY u.name_last, u.name_first, u.Id"),
                "UserId must break duplicate-name ties deterministically");
        assertTrue(method.contains("new UserRow("));
        assertTrue(method.contains("rs.getInt(\"Id\")"), "selection identity must remain Users.Id");
    }

    private static String method(String source, String signature) {
        int start = source.indexOf(signature);
        assertTrue(start >= 0, "Missing method: " + signature);
        int brace = source.indexOf('{', start);
        int depth = 0;
        for (int i = brace; i < source.length(); i++) {
            if (source.charAt(i) == '{') depth++;
            if (source.charAt(i) == '}' && --depth == 0) return source.substring(start, i + 1);
        }
        fail("Unterminated method: " + signature);
        return "";
    }

    private static int occurrences(String text, String needle) {
        int count = 0;
        for (int at = 0; (at = text.indexOf(needle, at)) >= 0; at += needle.length()) count++;
        return count;
    }
}
