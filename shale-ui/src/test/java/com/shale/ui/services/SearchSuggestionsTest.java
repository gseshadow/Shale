package com.shale.ui.services;

import static org.junit.jupiter.api.Assertions.*;
import com.shale.ui.services.SearchService.Suggestion;
import com.shale.ui.services.SearchService.SuggestionType;
import java.util.List;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;

class SearchSuggestionsTest {
    @Test void categoriesPrecedeMatchRankAndTiesUseNameThenIdentity() {
        var rows = List.of(row(SuggestionType.TASK, 1, "Exact", 0),
                row(SuggestionType.CONTACT, 2, "Exact", 0), row(SuggestionType.CASE, 3, "Other", 2),
                row(SuggestionType.ORGANIZATION, 4, "Exact", 0), row(SuggestionType.CASE, 6, "Prefix", 1),
                row(SuggestionType.CASE, 5, "Exact", 0), row(SuggestionType.CASE, 8, "exact", 0));
        assertEquals(List.of(5L, 8L, 6L, 3L, 2L, 4L, 1L),
                SearchService.orderSuggestions(rows, 5, 10).stream().map(Suggestion::id).toList(),
                "Case category wins before Contact/Organization; rank and stable numeric identity order within it");
    }

    @Test void exactAndPrefixApplyAcrossExistingNameEmailAndPhoneMatches() {
        assertEquals(0, SearchService.matchRank(" ADA ", List.of("Ada"), List.of()));
        assertEquals(1, SearchService.matchRank("ada", List.of("Ada Lovelace"), List.of()));
        assertEquals(2, SearchService.matchRank("ada", List.of("Team Ada"), List.of()));
        assertEquals(0, SearchService.matchRank("ada@example.test", List.of("Ada", "ada@example.test"), List.of()));
        assertEquals(0, SearchService.matchRank("555-0123", List.of("Other"), List.of("(555) 0123")));
        assertEquals(1, SearchService.matchRank("555", List.of("Other"), List.of("(555) 0123")));
        assertEquals(2, SearchService.matchRank("0123", List.of("Other"), List.of("(555) 0123")));
    }

    @Test void perCategoryAndOverallLimitsKeepHighestPriorityResults() {
        var rows = new ArrayList<Suggestion>();
        for (var type : SuggestionType.values())
            for (int i = 10; i > 0; i--) rows.add(row(type, i, "Name", i % 3));
        var ordered = SearchService.orderSuggestions(rows, 3, 8);
        assertEquals(8, ordered.size());
        assertEquals(3, ordered.stream().filter(r -> r.type() == SuggestionType.CASE).count());
        assertEquals(3, ordered.stream().filter(r -> r.type() == SuggestionType.CONTACT).count());
        assertEquals(2, ordered.stream().filter(r -> r.type() == SuggestionType.ORGANIZATION).count());
        assertEquals(List.of(3L, 6L, 9L), ordered.subList(0, 3).stream().map(Suggestion::id).toList());
    }

    @Test void emptyStaleAndUnauthorizedDeletedSelectionsPerformNoProviderReads() {
        com.shale.core.runtime.DbSessionProvider db = () -> { throw new AssertionError("Unexpected database read"); };
        var service = service(db);
        assertTrue(service.suggest(7, " ", true, () -> true).rows().isEmpty());
        assertTrue(service.suggest(7, "Example", true, () -> false).rows().isEmpty());
        assertFalse(service.suggestionAvailable(7, "Example", row(SuggestionType.DELETED_CASE, 42, "Example", 0), false),
                "A Deleted Case selection must recheck current visibility permission before reading or navigating");
    }

    @Test void providerFailuresArePartialAndStaleLoadsStopBeforeTheNextProvider() {
        var count = new java.util.concurrent.atomic.AtomicInteger();
        var service = service(() -> {
            count.incrementAndGet();
            throw new IllegalStateException("Unavailable");
        });
        var result = service.suggest(7, "Example", false, () -> count.get() == 0);
        assertTrue(result.failed());
        assertEquals(1, count.get(), "A stale worker must stop between bounded providers");
    }

    private static SearchService service(com.shale.core.runtime.DbSessionProvider db) {
        return new SearchService(new com.shale.data.dao.CaseDao(db), new com.shale.data.dao.CaseSummaryDao(db),
                new com.shale.data.dao.ContactDao(db), new com.shale.data.dao.OrganizationDao(db),
                new com.shale.data.dao.UserDao(db), new com.shale.data.dao.TaskDao(db),
                new com.shale.data.dao.CalendarEventDao(db));
    }

    private static Suggestion row(SuggestionType type, long id, String name, int rank) {
        return new Suggestion(type, id, name, "#" + id, rank);
    }
}
