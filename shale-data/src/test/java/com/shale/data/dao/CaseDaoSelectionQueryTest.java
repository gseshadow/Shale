package com.shale.data.dao;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

final class CaseDaoSelectionQueryTest {
    @Test
    void selectorIsOneTenantScopedLightweightOrderedQuery() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/shale/data/dao/CaseDao.java"));
        int start = source.indexOf("public List<CaseSelectionOptionDto> listCaseSelectionOptions");
        int end = source.indexOf("/** page is 0-based; query/status filters", start);
        String method = source.substring(start, end);

        assertEquals(1, occurrences(method, "executeQuery()"), "one selector SQL round trip");
        assertEquals(1, occurrences(method, "new CaseSelectionOptionDto("), "mapping is inline, not N+1");
        assertTrue(method.contains("WHERE c.ShaleClientId = ?"));
        assertTrue(method.contains("ISNULL(c.IsDeleted, 0) = 0"));
        assertTrue(method.contains("ISNULL(cu.IsDeleted, 0) = 0"));
        assertTrue(method.contains("ORDER BY LOWER(COALESCE(c.Name, '')), c.Id"));
        assertTrue(method.contains("return List.copyOf(out)"));
        assertFalse(method.contains("COUNT("));
        assertFalse(method.contains("OFFSET "));
        assertFalse(method.contains("CaseParties"));
        assertFalse(method.contains("CaseUpdates"));
        assertFalse(method.contains("Description"));
        assertFalse(method.contains("StatuteOfLimitations"));
        assertFalse(method.contains("Task"));
    }

    @Test
    void projectionContainsOnlyFieldsRenderedByCalendarCaseCard() throws Exception {
        String dto = Files.readString(Path.of("../shale-core/src/main/java/com/shale/core/dto/CaseSelectionOptionDto.java"));
        assertTrue(dto.contains("long caseId"));
        assertTrue(dto.contains("String displayName"));
        assertTrue(dto.contains("String responsibleAttorneyName"));
        assertTrue(dto.contains("String responsibleAttorneyColor"));
        assertTrue(dto.contains("Boolean nonEngagementLetterSent"));
        assertEquals(5, occurrences(dto.substring(dto.indexOf("public record")), ",") + 1);
    }

    private static int occurrences(String text, String needle) {
        int count = 0;
        for (int at = text.indexOf(needle); at >= 0; at = text.indexOf(needle, at + needle.length())) count++;
        return count;
    }
}
