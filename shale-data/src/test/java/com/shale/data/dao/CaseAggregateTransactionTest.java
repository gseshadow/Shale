package com.shale.data.dao;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class CaseAggregateTransactionTest {
    @Test void commitsAllWorkOnTheSingleProvidedConnection() {
        List<String> calls = new ArrayList<>();
        Connection connection = connection(calls);
        String result = new CaseAggregateTransaction(() -> connection).execute(con -> {
            assertSame(connection, con);
            calls.add("case"); calls.add("dates"); calls.add("phi-audit"); calls.add("entity-audit"); calls.add("workflow");
            return "done";
        });
        assertEquals("done", result);
        assertEquals(List.of("getAutoCommit", "setAutoCommit:false", "case", "dates", "phi-audit", "entity-audit", "workflow", "commit", "setAutoCommit:true", "close"), calls);
    }

    @Test void rollsBackEntireAggregateWhenAnyStepFails() {
        List<String> calls = new ArrayList<>();
        Connection connection = connection(calls);
        assertThrows(IllegalStateException.class, () -> new CaseAggregateTransaction(() -> connection).execute(con -> {
            calls.add("case-insert"); calls.add("date-insert"); throw new IllegalStateException("audit failed");
        }));
        assertTrue(calls.contains("rollback"));
        assertFalse(calls.contains("commit"));
    }

    private static Connection connection(List<String> calls) {
        return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[]{Connection.class}, (proxy, method, args) -> {
            switch (method.getName()) {
                case "getAutoCommit" -> { calls.add("getAutoCommit"); return true; }
                case "setAutoCommit" -> { calls.add("setAutoCommit:" + args[0]); return null; }
                case "commit", "rollback", "close" -> { calls.add(method.getName()); return null; }
                case "isClosed" -> { return false; }
                case "unwrap" -> { return null; }
                case "isWrapperFor" -> { return false; }
                case "toString" -> { return "test-connection"; }
                default -> throw new UnsupportedOperationException(method.getName());
            }
        });
    }
}
