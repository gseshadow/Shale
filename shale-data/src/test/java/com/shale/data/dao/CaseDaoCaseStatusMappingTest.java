package com.shale.data.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.shale.core.dto.CaseStatusDto;

final class CaseDaoCaseStatusMappingTest {

    @Test
    void listCaseStatusesMappingKeepsRowsFromStatusLookup() {
        List<CaseDao.StatusRow> lookupRows = List.of(
                new CaseDao.StatusRow(1, "Prelitigation", 10, "0xFFFFFFFF", "open", "prelitigation", true, false),
                new CaseDao.StatusRow(2, "Closed", 80, "0xFF333333", "closed", "closed", true, false));

        List<CaseStatusDto> mapped = CaseDao.toCaseStatusDtos(lookupRows);

        assertEquals(2, mapped.size());
        assertEquals("Prelitigation", mapped.get(0).name());
        assertFalse(mapped.get(0).closed());
        assertEquals(10, mapped.get(0).sortOrder());
        assertEquals("0xFFFFFFFF", mapped.get(0).color());
        assertEquals("prelitigation", mapped.get(0).systemKey());
        assertEquals("Closed", mapped.get(1).name());
        assertTrue(mapped.get(1).closed());
    }
}
