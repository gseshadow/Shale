package com.shale.data.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class CaseTimelineWriterTest {
    @Test
    void appendBindsTenantActorCaseAndReadableContentOnCallerConnection() throws Exception {
        List<Object> values = new ArrayList<>();
        PreparedStatement statement = statement(values, 1);
        Connection connection = connection(statement);

        CaseTimelineWriter.append(connection, 42, 7, 9, CaseTimelineWriter.CASE_DATE_UPDATED,
                "changed Statute of Limitations", "from 2027-05-04 to 2027-06-01");

        assertEquals(42L, values.get(0));
        assertEquals(7, values.get(1));
        assertEquals(CaseTimelineWriter.CASE_DATE_UPDATED, values.get(2));
        assertEquals(9, values.get(4));
        assertEquals("changed Statute of Limitations", values.get(5));
        assertEquals("from 2027-05-04 to 2027-06-01", values.get(6));
    }

    @Test
    void failedTimelineInsertFailsClosed() {
        assertThrows(SQLException.class, () -> CaseTimelineWriter.append(connection(statement(new ArrayList<>(), 0)),
                42, 7, 9, CaseTimelineWriter.MATERIAL_REQUEST_UPDATED, "updated a Material Request", null));
    }

    private static Connection connection(PreparedStatement ps) {
        return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[]{Connection.class},
                (proxy, method, args) -> method.getName().equals("prepareStatement") ? ps : defaultValue(method.getReturnType()));
    }

    private static PreparedStatement statement(List<Object> values, int rows) {
        return (PreparedStatement) Proxy.newProxyInstance(PreparedStatement.class.getClassLoader(), new Class<?>[]{PreparedStatement.class},
                (proxy, method, args) -> {
                    if (method.getName().startsWith("set")) {
                        int index = (Integer) args[0];
                        while (values.size() < index) values.add(null);
                        values.set(index - 1, args[1]);
                    }
                    if (method.getName().equals("executeUpdate")) return rows;
                    return defaultValue(method.getReturnType());
                });
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        return null;
    }
}
