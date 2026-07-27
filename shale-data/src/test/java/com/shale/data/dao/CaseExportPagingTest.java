package com.shale.data.dao;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class CaseExportPagingTest {
    @Test void exportCollectsEveryMatchingPageBeyondOneHundred() {
        List<Integer> expected = IntStream.range(0, 251).boxed().toList();
        List<Integer> actual = CaseDao.collectAllExportPages(page -> {
            int from = page * 100;
            int to = Math.min(from + 100, expected.size());
            List<Integer> items = from >= expected.size() ? List.of() : expected.subList(from, to);
            return new CaseDao.PagedResult<>(items, page, 100, expected.size());
        });
        assertEquals(expected, actual);
    }
}
