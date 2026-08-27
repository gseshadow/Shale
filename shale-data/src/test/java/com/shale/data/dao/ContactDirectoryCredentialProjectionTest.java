package com.shale.data.dao;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class ContactDirectoryCredentialProjectionTest {
    @Test void realDirectoryReadRetainsGlobalHistoricalCredentialDefinition() {
        AtomicReference<String> directorySql = new AtomicReference<>();
        ContactDao dao = new ContactDao(() -> connection(directorySql));

        var page = dao.findDirectoryContactsPage(42, 7, 0, 25, "", com.shale.core.service.ContactServicePort.DirectoryFilters.EMPTY);

        assertEquals(1, page.items().size());
        var row = page.items().getFirst();
        assertEquals(101, row.id(), "contact ID");
        assertEquals(1, row.credentialAbbreviations().size(), "number of abbreviations returned by the DAO");
        assertEquals(List.of("M.D."), row.credentialAbbreviations());
        assertTrue(directorySql.get().contains("(d.ShaleClientId=a.ShaleClientId OR d.ShaleClientId IS NULL)"));
        assertFalse(directorySql.get().contains("d.IsActive=1 AND d.IsDeleted=0"));
    }

    private static Connection connection(AtomicReference<String> directorySql) {
        return proxy(Connection.class, (method, args) -> {
            if (method.equals("prepareStatement")) {
                String sql = (String) args[0];
                if (sql.contains("AS CredentialAbbreviations")) directorySql.set(sql);
                return statement(sql);
            }
            return defaultValue(method);
        });
    }

    private static PreparedStatement statement(String sql) {
        Map<Integer, Object> parameters = new HashMap<>();
        return proxy(PreparedStatement.class, (method, args) -> {
            if (method.startsWith("set") && args != null && args.length >= 2) {
                parameters.put((Integer) args[0], args[1]);
                return null;
            }
            if (method.equals("executeQuery")) {
                if (sql.contains("SESSION_CONTEXT")) return result(List.of(Map.of("1", 42)));
                if (sql.contains("INFORMATION_SCHEMA.COLUMNS")) {
                    String column = String.valueOf(parameters.get(2));
                    boolean exists = List.of("ShaleClientId", "DisplayName", "FirstName", "LastName", "IsDeleted").contains(column);
                    return result(exists ? List.of(Map.of("1", 1)) : List.of());
                }
                if (sql.contains("COUNT_BIG")) return result(List.of(Map.of("1", 1L)));
                if (sql.contains("AS CredentialAbbreviations")) return result(List.of(Map.of(
                        "Id", 101, "DisplayName", "Example Doctor", "Email", "doctor@example.test",
                        "Phone", "555", "CredentialAbbreviations", "M.D.")));
                throw new AssertionError("Unexpected SQL: " + sql);
            }
            return defaultValue(method);
        });
    }

    private static ResultSet result(List<Map<String, Object>> rows) {
        int[] index = {-1};
        return proxy(ResultSet.class, (method, args) -> {
            if (method.equals("next")) return ++index[0] < rows.size();
            Object value = args == null ? null : rows.get(index[0]).get(String.valueOf(args[0]));
            if (method.equals("getString")) return (String) value;
            if (method.equals("getInt")) return ((Number) value).intValue();
            if (method.equals("getLong")) return ((Number) value).longValue();
            return defaultValue(method);
        });
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, Invocation invocation) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                (ignored, method, args) -> invocation.call(method.getName(), args));
    }

    private static Object defaultValue(String method) { return method.equals("isClosed") ? false : null; }
    @FunctionalInterface private interface Invocation { Object call(String method, Object[] args) throws Throwable; }
}
