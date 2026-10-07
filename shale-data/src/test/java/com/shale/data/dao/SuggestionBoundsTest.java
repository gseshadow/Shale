package com.shale.data.dao;

import static org.junit.jupiter.api.Assertions.*;
import com.shale.core.runtime.DbSessionProvider;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Executes real DAO search paths with deterministic JDBC capture, including schema discovery. */
class SuggestionBoundsTest {
    @Test void everyProviderIsBoundedAfterRankingAndKeepsItsExistingVisibilityAndBindings() {
        var harness = new Harness();
        var bounds = new SuggestionBounds(3);
        new CaseSummaryDao(harness).searchActiveByName(7, "Ada", bounds);
        new CaseSummaryDao(harness).searchDeletedByName(7, "Ada", bounds);
        new ContactDao(harness).searchContacts(7, "Ada", bounds);
        new OrganizationDao(harness).searchOrganizations("Ada", bounds);
        new UserDao(harness).searchUsers(7, "Ada", bounds);
        new TaskDao(harness).searchTasks(7, "Ada", bounds);
        new CalendarEventDao(harness).searchCalendarEvents(7, "Ada", bounds);
        var searches = harness.statements.stream().filter(s -> s.sql.startsWith("SELECT * FROM (")).toList();
        assertEquals(7, searches.size(), "All universal-search providers must use their production bounded path");
        for (var statement : searches) {
            assertTrue(statement.sql.contains("THEN 0 WHEN ("), "Exact matches must sort before prefixes");
            assertTrue(statement.sql.contains("THEN 1 ELSE 2 END"), "Prefixes must sort before other matches");
            assertTrue(statement.sql.endsWith("OFFSET 0 ROWS FETCH NEXT ? ROWS ONLY"));
            assertFalse(statement.sql.contains("SELECT TOP (100)"), "Legacy cap cannot discard a better match before ranking");
            assertTrue(statement.sql.contains("ShaleClientId"), "Tenant predicates must survive wrapping");
            assertEquals(3, statement.parameters.get(statement.parameters.size()), "Limit must be the final bound parameter");
            assertEquals(statement.sql.chars().filter(c -> c == '?').count(), statement.parameters.size(),
                    "Every predicate/rank/limit placeholder must have exactly one binding");
            assertEquals(5, statement.timeout, "Suggestions must not monopolize the single worker");
        }
        assertTrue(searches.get(0).sql.contains("ISNULL(c.IsDeleted,0)=0"));
        assertTrue(searches.get(1).sql.contains("c.IsDeleted = 1"));
        assertTrue(searches.get(2).sql.contains("COALESCE(c.IsDeleted, 0) = 0"));
        assertTrue(searches.get(2).sql.contains("ContactEmailAddresses"));
        assertTrue(searches.get(2).sql.contains("ContactPhoneNumbers"));
        assertTrue(searches.get(3).sql.contains("o.IsDeleted = 0 OR o.IsDeleted IS NULL"));
        assertTrue(searches.get(4).sql.contains("u.is_deleted = 0"));
        assertTrue(searches.get(5).sql.contains("ISNULL(t.IsDeleted, 0) = 0"));
        assertTrue(searches.get(6).sql.contains("ISNULL(e.IsCancelled, 0) = 0"));
    }

    @Test void exactPrefixWildcardsAndSelectedIdentityRemainParameterized() throws Exception {
        var bounds = new SuggestionBounds(1, 42L);
        var texts = List.of("Name", "Email");
        var phones = List.of("Phone");
        String sql = bounds.sql("SELECT Id, Name, Email, Phone FROM dbo.Contacts WHERE ShaleClientId=? ORDER BY Name, Id",
                "Name", "Id", texts, phones);
        var statement = new Statement(sql);
        PreparedStatement ps = statement.proxy();
        ps.setInt(1, 7);
        bounds.bind(ps, 2, " A%_[23] ", texts, phones);
        assertTrue(sql.contains("WHERE Id = ? ORDER BY"));
        assertFalse(sql.contains("A%_"), "User text must never be interpolated into SQL");
        assertEquals(42L, statement.parameters.get(2));
        assertEquals("a%_[23]", statement.parameters.get(3));
        assertEquals("a[%][_][[]23]%", statement.parameters.get(7), "SQL wildcards in the rank prefix are literal");
        assertEquals(sql.chars().filter(c -> c == '?').count(), statement.parameters.size());
        assertThrows(IllegalArgumentException.class, () -> new SuggestionBounds(0));
        assertThrows(IllegalArgumentException.class, () -> new SuggestionBounds(6));
    }

    @Test void oldFullSearchKeepsItsOriginalUnboundedAndLegacyCappedBehavior() {
        var harness = new Harness();
        new CaseSummaryDao(harness).searchActiveByName(7, "Ada");
        new TaskDao(harness).searchTasks(7, "Ada");
        assertTrue(harness.statements.stream().anyMatch(s -> s.sql.contains("SELECT TOP (100)")));
        assertFalse(harness.statements.stream().anyMatch(s -> s.sql.contains("FETCH NEXT")));
        var wrongTenant = new Harness();
        assertThrows(IllegalStateException.class, () -> new CaseSummaryDao(wrongTenant).searchActiveByName(8, "Ada", new SuggestionBounds(3)));
    }

    private static class Harness implements DbSessionProvider {
        final List<Statement> statements = new ArrayList<>();
        @Override public Connection requireConnection() {
            return (Connection) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Connection.class},
                    (proxy, method, args) -> {
                        if (method.getName().equals("prepareStatement")) {
                            var statement = new Statement((String) args[0]);
                            statements.add(statement);
                            return statement.proxy();
                        }
                        return defaultValue(method.getReturnType());
                    });
        }
    }

    private static class Statement {
        final String sql;
        final Map<Integer, Object> parameters = new HashMap<>();
        int timeout;
        Statement(String sql) { this.sql = sql; }
        PreparedStatement proxy() {
            return (PreparedStatement) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{PreparedStatement.class},
                    (proxy, method, args) -> {
                        if (method.getName().equals("setQueryTimeout")) timeout = (Integer) args[0];
                        else if (method.getName().startsWith("set") && args.length >= 2) parameters.put((Integer) args[0], args[1]);
                        if (method.getName().equals("executeQuery")) {
                            boolean scalar = sql.contains("SESSION_CONTEXT") || sql.contains("INFORMATION_SCHEMA.COLUMNS");
                            return rows(scalar);
                        }
                        return defaultValue(method.getReturnType());
                    });
        }
    }

    private static ResultSet rows(boolean scalar) {
        int[] index = {0};
        return (ResultSet) Proxy.newProxyInstance(SuggestionBoundsTest.class.getClassLoader(), new Class<?>[]{ResultSet.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "next" -> scalar && index[0]++ == 0;
                    case "getObject", "getInt" -> 7;
                    case "wasNull" -> false;
                    default -> defaultValue(method.getReturnType());
                });
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive() || type == void.class) return null;
        if (type == boolean.class) return false;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == double.class) return 0D;
        return null;
    }
}
