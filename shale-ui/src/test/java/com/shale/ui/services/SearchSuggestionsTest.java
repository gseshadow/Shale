package com.shale.ui.services;

import static org.junit.jupiter.api.Assertions.*;
import com.shale.ui.services.SearchService.Suggestion;
import com.shale.ui.services.SearchService.SuggestionType;
import java.util.List;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;

class SearchSuggestionsTest {
    @Test void productionSuggestionAndSelectionDelegateToTheNarrowBatch() {
        var queries = new ArrayList<String>();
        var ids = new ArrayList<Long>();
        com.shale.core.runtime.DbSessionProvider db = () -> (java.sql.Connection) java.lang.reflect.Proxy.newProxyInstance(
                java.sql.Connection.class.getClassLoader(), new Class<?>[]{java.sql.Connection.class}, (p,m,a) -> {
                    if (!m.getName().equals("prepareStatement")) return null;
                    queries.add((String)a[0]);
                    return java.lang.reflect.Proxy.newProxyInstance(java.sql.PreparedStatement.class.getClassLoader(),
                            new Class<?>[]{java.sql.PreparedStatement.class}, (sp,sm,sa) -> {
                                if (sm.getName().equals("setLong")) ids.add((Long)sa[1]);
                                if (sm.getName().equals("executeQuery")) {
                                    boolean[] next={true};
                                    return java.lang.reflect.Proxy.newProxyInstance(java.sql.ResultSet.class.getClassLoader(),
                                            new Class<?>[]{java.sql.ResultSet.class}, (rp,rm,ra) -> switch(rm.getName()) {
                                                case "next" -> { boolean value=next[0];next[0]=false;yield value; }
                                                case "getInt" -> 7;
                                                case "getObject" -> ra[0].equals("TenantId") ? 7 : null;
                                                default -> null;
                                            });
                                }
                                if (sm.getName().equals("execute")) return false;
                                if (sm.getName().equals("getUpdateCount")) return -1;
                                return null;
                            });
                });
        var service = service(db);
        assertTrue(service.suggest(7,"Example",false,()->true).rows().isEmpty());
        assertFalse(service.suggestionAvailable(7,"C-42",row(SuggestionType.CASE,42,"Example",0),false));
        assertEquals(4,queries.size(),"Each service call must delegate to one metadata check and one narrow batch");
        assertEquals(List.of(42L),ids,"Selection must recheck the captured record identity");
        for (String sql : List.of(queries.get(1),queries.get(3))) {
            assertTrue(sql.contains("@rows TABLE(Category int,Id bigint,Name nvarchar(max),Detail nvarchar(max),MatchRank int)"));
            assertFalse(sql.contains("CaseDates")); assertFalse(sql.contains("Classification"));
        }
    }

    @Test void categoriesPrecedeMatchRankAndTiesUseNameThenIdentity() {
        var rows = List.of(row(SuggestionType.TASK, 1, "Exact", 0),
                row(SuggestionType.CONTACT, 2, "Exact", 0), row(SuggestionType.CASE, 3, "Other", 2),
                row(SuggestionType.ORGANIZATION, 4, "Exact", 0), row(SuggestionType.CASE, 6, "Prefix", 1),
                row(SuggestionType.CASE, 5, "Exact", 0), row(SuggestionType.CASE, 8, "exact", 0));
        assertEquals(List.of(5L, 8L, 6L, 3L, 2L, 4L, 1L),
                SearchService.orderSuggestions(rows, 5, 10).stream().map(Suggestion::id).toList(),
                "Case category wins before Contact/Organization; rank and stable numeric identity order within it");
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

    @Test void connectionFailureDoesNotRetryEveryCategory() {
        var count = new java.util.concurrent.atomic.AtomicInteger();
        var service = service(() -> {
            count.incrementAndGet();
            throw new IllegalStateException("Unavailable");
        });
        assertThrows(IllegalStateException.class, () -> service.suggest(7, "Example", false, () -> count.get() == 0));
        assertEquals(1, count.get(), "A failed acquisition must not retry once per category");
    }

    private static SearchService service(com.shale.core.runtime.DbSessionProvider db) {
        return new SearchService(new com.shale.data.dao.CaseDao(db), new com.shale.data.dao.CaseSummaryDao(db),
                new com.shale.data.dao.ContactDao(db), new com.shale.data.dao.OrganizationDao(db),
                new com.shale.data.dao.UserDao(db), new com.shale.data.dao.TaskDao(db),
                new com.shale.data.dao.CalendarEventDao(db), new com.shale.data.dao.SuggestionDao(db));
    }

    private static Suggestion row(SuggestionType type, long id, String name, int rank) {
        return new Suggestion(type, id, name, "#" + id, rank);
    }
}
