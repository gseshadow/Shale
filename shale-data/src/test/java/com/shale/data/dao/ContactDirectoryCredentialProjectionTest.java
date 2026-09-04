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
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class ContactDirectoryCredentialProjectionTest {
    @Test void realDirectoryReadRetainsGlobalHistoricalCredentialDefinition() {
        AtomicReference<String> directorySql = new AtomicReference<>();
        AtomicBoolean connectionClosed = new AtomicBoolean();
        AtomicBoolean statementClosed = new AtomicBoolean();
        AtomicBoolean resultSetClosed = new AtomicBoolean();
        ContactDao dao = new ContactDao(() -> connection(directorySql, connectionClosed, statementClosed, resultSetClosed));

        var page = dao.findDirectoryContactsPage(42, 7, 0, 25, "", com.shale.core.service.ContactServicePort.DirectoryFilters.EMPTY);

        assertEquals(1, page.items().size());
        var row = page.items().getFirst();
        assertEquals(101, row.id(), "contact ID");
        assertEquals(1, row.credentialAbbreviations().size(), "number of abbreviations returned by the DAO");
        assertEquals(List.of("M.D."), row.credentialAbbreviations());
        assertNotNull(directorySql.get(), "the real page query must execute");
        assertTrue(directorySql.get().contains("a.ContactId IN (?)"), "bounded page enrichment");
        assertTrue(directorySql.get().contains("ORDER BY a.ContactId,a.DisplayOrder,d.SortOrder,d.Name,d.Id,a.Id"));
        assertTrue(directorySql.get().contains("(d.ShaleClientId=a.ShaleClientId OR d.ShaleClientId IS NULL)"));
        assertFalse(directorySql.get().contains("d.IsActive=1 AND d.IsDeleted=0"));
        assertTrue(resultSetClosed.get(), "ResultSet.close must complete");
        assertTrue(statementClosed.get(), "PreparedStatement.close must complete");
        assertTrue(connectionClosed.get(), "Connection.close must complete");
    }

    private static Connection connection(AtomicReference<String> directorySql, AtomicBoolean connectionClosed,
            AtomicBoolean statementClosed, AtomicBoolean resultSetClosed) {
        return proxy(Connection.class, (method, args) -> {
            if (method.equals("close")) { connectionClosed.set(true); return null; }
            if (method.equals("prepareStatement")) {
                String sql = (String) args[0];
                if (sql.contains("FROM dbo.ContactCredentials a") && !sql.contains("ContactContactTypes")) directorySql.set(sql);
                return statement(sql, statementClosed, resultSetClosed);
            }
            return defaultValue(method);
        });
    }

    private static PreparedStatement statement(String sql, AtomicBoolean statementClosed, AtomicBoolean resultSetClosed) {
        Map<Integer, Object> parameters = new HashMap<>();
        return proxy(PreparedStatement.class, (method, args) -> {
            if (method.equals("close")) { statementClosed.set(true); return null; }
            if (method.startsWith("set") && args != null && args.length >= 2) {
                parameters.put((Integer) args[0], args[1]);
                return null;
            }
            if (method.equals("executeQuery")) {
                if (sql.contains("SESSION_CONTEXT")) return result(List.of(Map.of("1", 42)), resultSetClosed);
                if (sql.contains("INFORMATION_SCHEMA.COLUMNS")) {
                    String column = String.valueOf(parameters.get(2));
                    boolean exists = List.of("ShaleClientId", "DisplayName", "FirstName", "LastName", "IsDeleted").contains(column);
                    return result(exists ? List.of(Map.of("1", 1)) : List.of(), resultSetClosed);
                }
                if (sql.contains("COUNT_BIG")) return result(List.of(Map.of("1", 1L)), resultSetClosed);
                if (sql.contains("OFFSET ? ROWS")) return result(List.of(Map.of(
                        "Id", 101, "DisplayName", "Example Doctor", "Email", "doctor@example.test",
                        "Phone", "555")), resultSetClosed);
                if (sql.contains("ContactContactTypes")) return result(List.of(),resultSetClosed);
                if (sql.contains("FROM dbo.ContactCredentials a")) return result(List.of(Map.of(
                        "ContactId", 101, "Abbreviation", "M.D.")), resultSetClosed);
                throw new AssertionError("Unexpected SQL: " + sql);
            }
            return defaultValue(method);
        });
    }

    private static ResultSet result(List<Map<String, Object>> rows, AtomicBoolean resultSetClosed) {
        int[] index = {-1};
        boolean[] wasNull = {false};
        return proxy(ResultSet.class, (method, args) -> {
            if (method.equals("close")) { resultSetClosed.set(true); return null; }
            if (method.equals("next")) return ++index[0] < rows.size();
            if (method.equals("wasNull")) return wasNull[0];
            Object value = args == null ? null : rows.get(index[0]).get(String.valueOf(args[0]));
            if (method.startsWith("get")) wasNull[0] = value == null;
            if (method.equals("getString")) return (String) value;
            if (method.equals("getInt")) return value == null ? 0 : ((Number) value).intValue();
            if (method.equals("getLong")) return value == null ? 0L : ((Number) value).longValue();
            return defaultValue(method);
        });
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, Invocation invocation) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                (ignored, method, args) -> {
                    Object value = invocation.call(method.getName(), args);
                    if (method.getReturnType() == void.class) return null;
                    return value == null && method.getReturnType().isPrimitive()
                            ? primitiveDefault(method.getReturnType()) : value;
                });
    }

    private static Object defaultValue(String method) { return method.equals("isClosed") ? false : null; }
    private static Object primitiveDefault(Class<?> type) {
        if (type == boolean.class) return false;
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0F;
        if (type == double.class) return 0D;
        if (type == char.class) return '\0';
        throw new AssertionError("Unhandled primitive return type: " + type);
    }
    @FunctionalInterface private interface Invocation { Object call(String method, Object[] args) throws Throwable; }
}
