package com.shale.data.dao;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

/** Guards narrow autocomplete SQL and JDBC work volume; not a SQL Server execution-plan test. */
class SuggestionDaoTest {
    @Test void boundedUnicodeDeclarationsStayWithinSqlServerLimits() {
        String sql = SuggestionDao.batchSql("");
        var lengths = java.util.regex.Pattern.compile("(?i)nvarchar\\((\\d+)\\)").matcher(sql);
        assertTrue(lengths.find(), "Batch must declare Unicode parameters");
        do {
            assertTrue(Integer.parseInt(lengths.group(1)) <= 4000,
                    "Bounded nvarchar over 4000 fails SQL Server compilation with error 2717");
        } while (lengths.find());
        assertTrue(sql.contains("@prefix nvarchar(4000)=?, @contains nvarchar(4000)=?"));
    }

    @Test void unicodeBindingsAndLongestEscapedPatternAreNotTruncated() {
        var fixture = new JdbcFixture();
        // 1 Unicode character + 1,332 escaped percent signs + 1 plain character = 3,998 code units.
        String input = "漢" + "%".repeat(1332) + "x";
        new SuggestionDao(fixture::connection).search(7, input, false, 3, 18, null, null,
                () -> true, ignored -> { });
        assertEquals(input, fixture.bindings.get(2));
        assertEquals("漢" + "[%]".repeat(1332) + "x%", fixture.bindings.get(3));
        assertEquals("%漢" + "[%]".repeat(1332) + "x%", fixture.bindings.get(4));
        assertEquals(4000, ((String) fixture.bindings.get(4)).length());
        assertEquals(List.of(2, 3, 4), fixture.unicodeBindings,
                "All search strings must use explicit Unicode JDBC bindings");
        var rejected = new JdbcFixture();
        new SuggestionDao(rejected::connection).search(7, input + "x", false, 3, 18, null, null,
                () -> true, ignored -> { });
        assertEquals(0, rejected.acquisitions, "Overlong LIKE patterns must be rejected before database work");
    }

    @Test void identifyingFieldsOnlyAndRankBeforeBounds() {
        String sql = SuggestionDao.batchSql(" AND ISNULL(u.is_deleted,0)=0");
        for (String field : List.of("c.Name", "c.CaseNumber", "c.OfficePrinterCode", "c.Id", "c.FirstName", "c.LastName",
                "o.Name", "u.name_first", "u.name_last", "t.Title", "e.Title"))
            assertTrue(sql.contains(field), "Suggestions must match identifying field " + field);
        for (String retired : List.of("Description", "Notes", "Email", "Phone", "CaseDates", "CaseUsers", "CaseStatuses",
                "TaskAssignments", "DueAt", "StartsAt", "RowVer", "Credential", "SELECT *"))
            assertFalse(sql.contains(retired), "Suggestions must not hydrate/search " + retired);
        assertTrue(sql.contains("CASE WHEN (f.Name=@text"), "Exact matches must rank first inside SQL");
        assertTrue(sql.contains("THEN 0 WHEN (f.Name LIKE @prefix"), "Prefixes must rank before substring matches");
        assertTrue(sql.contains("SELECT TOP (@limit)"));
        assertTrue(sql.contains("SELECT TOP (@total) Category,Id,Name,Detail,MatchRank"));
        assertTrue(sql.contains("ORDER BY Category,MatchRank,LOWER(COALESCE(Name,'')),Id"));
        assertEquals(7, java.util.regex.Pattern.compile("WHERE [coute]\\.ShaleClientId=@tenant AND")
                .matcher(sql).results().count(), "Every category needs explicit tenant equality as well as RLS");
        assertTrue(sql.contains("c.IsDeleted=1"));
        assertTrue(sql.contains("@deleted=1"));
        assertTrue(sql.contains("actor.is_admin=1"));
        assertTrue(sql.contains("SESSION_CONTEXT(N'PrincipalUserId')"));
        assertTrue(sql.contains("ISNULL(t.IsDeleted,0)=0"));
        assertTrue(sql.contains("ISNULL(e.IsCancelled,0)=0"));
        assertTrue(sql.contains("IF ISNULL(TRY_CONVERT(int,SESSION_CONTEXT"));
    }

    @Test void oneConnectionOneMetadataCheckOneBatchAndOnlyDisplayMapping() {
        var fixture = new JdbcFixture();
        var callbacks = new ArrayList<Runnable>();
        var result = new SuggestionDao(fixture::connection).search(7, " A%_[ ", false, 3, 18,
                null, null, () -> true, callbacks::add);
        assertEquals(List.of(new SuggestionDao.Row(SuggestionDao.Category.CASE, 42, "Example", "C-42", 0)), result.rows());
        assertFalse(result.failed());
        assertEquals(1, fixture.acquisitions);
        assertEquals(2, fixture.sql.size(), "No per-category acquisitions, schema probes or related-result loads");
        assertEquals(7, fixture.bindings.get(1));
        assertEquals("a%_[", fixture.bindings.get(2));
        assertEquals("a[%][_][[]%", fixture.bindings.get(3), "LIKE metacharacters must remain literal");
        assertEquals("%a[%][_][[]%", fixture.bindings.get(4));
        assertEquals(3, fixture.bindings.get(5)); assertEquals(18, fixture.bindings.get(6));
        assertEquals(false, fixture.bindings.get(7)); assertEquals(-1, fixture.bindings.get(8));
        assertTrue(fixture.sql.get(1).contains("u.IsRemoved"));
        assertTrue(fixture.sql.get(1).contains("u.IsActive"));
        assertTrue(fixture.sql.get(1).contains("u.IsDeleted"));
        assertTrue(fixture.sql.get(1).contains("u.is_deleted"));
        assertEquals(List.of(5, 5), fixture.timeouts);
        assertTrue(fixture.closed);
        assertNull(callbacks.getLast(), "Never retain a cancellation callback for a released connection");
    }

    @Test void mismatchedTenantCannotRunBatchAndSelectedIdIsBoundBeforeRanking() {
        var mismatched = new JdbcFixture(); mismatched.tenant = 8;
        assertThrows(IllegalStateException.class, () -> new SuggestionDao(mismatched::connection).search(7, "Example", true,
                3, 18, null, null, () -> true, ignored -> { }));
        assertEquals(1, mismatched.sql.size()); assertTrue(mismatched.closed);
        var fixture = new JdbcFixture(); var selected = fixture;
        new SuggestionDao(selected::connection).search(7, "C-42", false, 1, 1, SuggestionDao.Category.CASE, 42L,
                () -> true, ignored -> { });
        assertEquals(42L, fixture.bindings.get(9));
        assertEquals(0, fixture.bindings.get(8));
        assertTrue(fixture.sql.get(1).contains("(@id IS NULL OR c.Id=@id)"));
    }

    @Test void cancelledOrEmptyRequestsAvoidReadsAndRaceDuringAcquisitionStopsBeforeSql() {
        var fixture = new JdbcFixture(); var dao = new SuggestionDao(fixture::connection);
        assertTrue(dao.search(7, " ", false, 3, 18, null, null, () -> true, ignored -> { }).rows().isEmpty());
        assertTrue(dao.search(7, "Example", false, 3, 18, null, null, () -> false, ignored -> { }).rows().isEmpty());
        assertTrue(dao.search(7, "%".repeat(2048), false, 3, 18, null, null, () -> true, ignored -> { }).rows().isEmpty(),
                "Oversized escaped LIKE patterns must not be truncated into an unintended match");
        assertEquals(0, fixture.acquisitions);
        var current = new AtomicBoolean(true);
        dao = new SuggestionDao(() -> { current.set(false); return fixture.connection(); });
        assertTrue(dao.search(7, "Example", false, 3, 18, null, null, current::get, ignored -> { }).rows().isEmpty());
        assertTrue(fixture.sql.isEmpty()); assertTrue(fixture.closed);
    }

    @Test void partialProviderFailureRetainsOtherCategoriesAndCancellationTargetsOnlyActiveStatement() {
        var fixture = new JdbcFixture(); fixture.error = 208;
        var active = new ArrayList<Runnable>();
        var result = new SuggestionDao(fixture::connection).search(7, "Example", true, 3, 18, null, null,
                () -> true, callback -> { if (callback != null) { active.add(callback); callback.run(); } });
        assertTrue(result.failed()); assertEquals(1, result.rows().size());
        assertEquals(2, fixture.cancels, "Tenant check and batch each register their own statement");
    }

    private static <T> T proxy(Class<T> type, java.lang.reflect.InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
    }
    private static Object fallback(Class<?> type) {
        if (type == boolean.class) return false;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        return null;
    }
    private static ResultSet rows(List<Map<String, Object>> rows, String firstColumn) {
        int[] index = {-1};
        return proxy(ResultSet.class, (p, m, a) -> switch (m.getName()) {
            case "next" -> ++index[0] < rows.size();
            case "getMetaData" -> proxy(ResultSetMetaData.class, (mp, mm, ma) -> mm.getName().equals("getColumnLabel") ? firstColumn : fallback(mm.getReturnType()));
            case "getObject", "getString" -> rows.get(index[0]).get(a[0]);
            case "getInt" -> ((Number) rows.get(index[0]).get(a[0])).intValue();
            case "getLong" -> ((Number) rows.get(index[0]).get(a[0])).longValue();
            default -> fallback(m.getReturnType());
        });
    }
    private static final class JdbcFixture {
        int tenant=7, acquisitions, cancels, error;
        boolean closed;
        final List<String> sql = new ArrayList<>();
        final List<Integer> timeouts = new ArrayList<>();
        final List<Integer> unicodeBindings = new ArrayList<>();
        final Map<Integer,Object> bindings = new HashMap<>();
        Connection connection() {
            acquisitions++;
            return proxy(Connection.class, (p,m,a) -> switch (m.getName()) {
                case "close" -> { closed=true; yield null; }
                case "prepareStatement" -> {
                    String statement=(String)a[0]; sql.add(statement); int[] result={0};
                    yield proxy(PreparedStatement.class, (sp,sm,sa) -> switch (sm.getName()) {
                        case "setInt", "setLong", "setString", "setBoolean" -> { bindings.put((Integer)sa[0],sa[1]); yield null; }
                        case "setNString" -> { bindings.put((Integer)sa[0],sa[1]); unicodeBindings.add((Integer)sa[0]); yield null; }
                        case "setNull" -> { bindings.put((Integer)sa[0],null); yield null; }
                        case "setQueryTimeout" -> { timeouts.add((Integer)sa[0]); yield null; }
                        case "cancel" -> { cancels++; yield null; }
                        case "executeQuery" -> rows(List.of(Map.of("TenantId",tenant,"RemovedColumn",1,"ActiveColumn",1,
                                "DeletedColumn",1,"LegacyDeletedColumn",1)), "TenantId");
                        case "execute" -> true;
                        case "getResultSet" -> result[0]==0
                                ? rows(List.of(Map.of("Category",0,"Id",42L,"Name","Example","Detail","C-42","MatchRank",0)), "Category")
                                : rows(List.of(Map.of("Provider",0,"ElapsedMs",12L,"ResultCount",1,"ErrorCode",error)), "Provider");
                        case "getMoreResults" -> ++result[0]==1;
                        case "getUpdateCount" -> -1;
                        default -> fallback(sm.getReturnType());
                    });
                }
                default -> fallback(m.getReturnType());
            });
        }
    }
}
