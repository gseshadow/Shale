package com.shale.data.dao;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Collections;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class NewIntakeContactPersistenceRegressionTest {
    private record Execution(String sql, Map<Integer, Object> bindings) { }

    @Test
    void clientAndCallerUseIndependentAuthoritativeStructuredContactPoints() throws Exception {
        List<Execution> executions = new ArrayList<>();
        CaseDao dao = dao();
        CaseDao.NewIntakeCreateRequest request = request();
        Connection connection = recordingConnection(executions, null);

        invokeContactPoints(dao, connection, request, 101,
                request.clientPhone(), request.clientEmail(), request.clientAddress());
        invokeContactPoints(dao, connection, request, 202,
                request.callerPhone(), request.callerEmail(), request.callerAddress());

        List<Execution> writes = executions.stream().filter(e -> !isEntityAudit(e)).toList();
        assertEquals(6, writes.size());
        assertPoint(writes.get(0), "ContactPhoneNumbers", 101, "MOBILE", "(555) 101-0001", "5551010001");
        assertPoint(writes.get(1), "ContactEmailAddresses", 101, "PERSONAL", "client@example.test", "client@example.test");
        assertPoint(writes.get(2), "ContactAddresses", 101, "HOME", "101 Client Street", null);
        assertPoint(writes.get(3), "ContactPhoneNumbers", 202, "MOBILE", "(555) 202-0002", "5552020002");
        assertPoint(writes.get(4), "ContactEmailAddresses", 202, "PERSONAL", "caller@example.test", "caller@example.test");
        assertPoint(writes.get(5), "ContactAddresses", 202, "HOME", "202 Caller Avenue", null);
        assertTrue(writes.get(2).sql().contains("LegacyAddressText"));

        List<Execution> audits = executions.stream().filter(NewIntakeContactPersistenceRegressionTest::isEntityAudit).toList();
        assertEquals(6, audits.size());
        assertAudit(audits.get(0), "CONTACT_PHONE_NUMBER", 101, "MOBILE");
        assertAudit(audits.get(1), "CONTACT_EMAIL_ADDRESS", 101, "PERSONAL");
        assertAudit(audits.get(2), "CONTACT_ADDRESS", 101, "HOME");
        assertAudit(audits.get(3), "CONTACT_PHONE_NUMBER", 202, "MOBILE");
        assertAudit(audits.get(4), "CONTACT_EMAIL_ADDRESS", 202, "PERSONAL");
        assertAudit(audits.get(5), "CONTACT_ADDRESS", 202, "HOME");
    }

    @Test
    void scalarContactInsertRetainsAuthoritativeFieldsAndOmitsRetiredPointColumns() throws Exception {
        List<Execution> executions = new ArrayList<>();
        CaseDao dao = dao();
        Method insert = CaseDao.class.getDeclaredMethod("insertContact", Connection.class, String.class,
                String.class, String.class, LocalDate.class, String.class, boolean.class, boolean.class,
                int.class, Timestamp.class);
        insert.setAccessible(true);
        int id = (int) insert.invoke(dao, recordingConnection(executions, null), "Client Display",
                "Client", "Person", LocalDate.of(1984, 2, 3), "Client condition", true, true, 7,
                Timestamp.valueOf("2026-09-01 12:00:00"));

        assertEquals(1001, id);
        assertEquals(1, executions.size());
        Execution scalar = executions.getFirst();
        for (String column : List.of("Name", "FirstName", "LastName", "DateOfBirth", "Condition",
                "IsDeceased", "IsClient"))
            assertTrue(scalar.sql().matches("(?s).*\\b" + column + "\\b.*"), "missing scalar column " + column);
        for (String retired : List.of("PhoneCell", "EmailPersonal", "AddressHome"))
            assertFalse(scalar.sql().matches("(?s).*\\b" + retired + "\\b.*"), "retired column " + retired);
        assertEquals("Client Display", scalar.bindings().get(1));
        assertEquals("Client", scalar.bindings().get(2));
        assertEquals("Person", scalar.bindings().get(3));
        assertEquals(java.sql.Date.valueOf("1984-02-03"), scalar.bindings().get(4));
        assertEquals("Client condition", scalar.bindings().get(5));
        assertEquals(true, scalar.bindings().get(6));
        assertEquals(true, scalar.bindings().get(7));
    }

    @Test
    void blankOptionalPointsWriteNothingAndStructuredFailurePropagatesToTransactionRollback() throws Exception {
        CaseDao dao = dao();
        List<Execution> blankExecutions = new ArrayList<>();
        invokeContactPoints(dao, recordingConnection(blankExecutions, null), request(), 101, " ", null, "");
        assertTrue(blankExecutions.isEmpty(), "blank optional points must not create child rows");

        List<String> transactionCalls = new ArrayList<>();
        Connection failing = transactionalConnection(transactionCalls, "ContactEmailAddresses");
        RuntimeException failure = assertThrows(RuntimeException.class,
                () -> new CaseAggregateTransaction(() -> failing).execute(connection -> {
                    try {
                        invokeContactPoints(dao, connection, request(), 101,
                                "555-101-0001", "client@example.test", "101 Client Street");
                        return null;
                    } catch (Exception ex) {
                        throw new RuntimeException(ex);
                    }
                }));
        assertNotNull(failure.getCause());
        assertTrue(transactionCalls.contains("rollback"), "structured-point failure must roll back the aggregate");
        assertFalse(transactionCalls.contains("commit"), "failed aggregate must never commit");
    }

    @Test
    void entityAuditFailureStillRollsBackTheAggregate() {
        CaseDao dao = dao();
        List<String> transactionCalls = new ArrayList<>();
        Connection failing = transactionalConnection(transactionCalls, "EntityActionAuditLog");

        assertThrows(RuntimeException.class, () -> new CaseAggregateTransaction(() -> failing).execute(connection -> {
            try {
                invokeContactPoints(dao, connection, request(), 101,
                        "555-101-0001", "client@example.test", "101 Client Street");
                return null;
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        }));

        assertTrue(transactionCalls.contains("rollback"));
        assertFalse(transactionCalls.contains("commit"));
    }

    @Test
    void intakeMethodKeepsSupplementalWritesBeforeItsCommitUsingRobustMethodExtraction() throws Exception {
        String method = extractMethod(Files.readString(Path.of("src/main/java/com/shale/data/dao/CaseDao.java")),
                "public NewIntakeCreateResult createIntake(");
        int clientPoints = method.indexOf("insertIntakeContactPoints(con, request, clientContactId");
        int caller = method.indexOf("resolveCallerContactId(con, request, clientContactId");
        int commit = method.indexOf("con.commit()");
        int rollback = method.indexOf("con.rollback()");
        assertAll(
                () -> assertTrue(clientPoints >= 0, "createIntake must persist Client structured points"),
                () -> assertTrue(caller >= 0, "createIntake must create or resolve the Caller in the same method"),
                () -> assertTrue(commit > clientPoints && commit > caller, "supplemental work must precede commit"),
                () -> assertTrue(rollback > commit, "the createIntake catch path must retain rollback"));
    }

    private static CaseDao dao() { return new CaseDao(() -> { throw new AssertionError("unexpected connection request"); }); }

    private static void invokeContactPoints(CaseDao dao, Connection connection,
            CaseDao.NewIntakeCreateRequest request, int contactId, String phone, String email, String address)
            throws Exception {
        Method method = CaseDao.class.getDeclaredMethod("insertIntakeContactPoints", Connection.class,
                CaseDao.NewIntakeCreateRequest.class, int.class, String.class, String.class, String.class);
        method.setAccessible(true);
        try {
            method.invoke(dao, connection, request, contactId, phone, email, address);
        } catch (InvocationTargetException ex) {
            if (ex.getCause() instanceof Exception cause) throw cause;
            throw ex;
        }
    }

    private static void assertPoint(Execution execution, String table, int contactId, String kind,
            String displayValue, String normalizedValue) {
        assertTrue(execution.sql().contains("INSERT dbo." + table), execution.sql());
        assertEquals(7, execution.bindings().get(1));
        assertEquals(contactId, execution.bindings().get(2));
        assertEquals(kind, execution.bindings().get(3));
        assertEquals(displayValue, execution.bindings().get(4));
        if (normalizedValue != null) assertEquals(normalizedValue, execution.bindings().get(5));
    }

    private static boolean isEntityAudit(Execution execution) {
        return execution.sql().contains("EntityActionAuditLog");
    }

    private static void assertAudit(Execution execution, String entityType, int contactId, String kind) {
        assertEquals(7, execution.bindings().get(1));
        assertEquals(9, execution.bindings().get(2));
        assertEquals(entityType, execution.bindings().get(3));
        assertEquals("CREATED", execution.bindings().get(5));
        assertEquals("CONTACT", execution.bindings().get(7));
        assertEquals((long) contactId, execution.bindings().get(8));
        String metadata = (String) execution.bindings().get(11);
        assertNotNull(metadata);
        assertTrue(metadata.contains("\"CONTACT_ID\":\"" + contactId + "\""));
        assertTrue(metadata.contains("\"KIND\":\"" + kind + "\""));
        assertTrue(metadata.contains("\"PRIMARY\":\"true\""));
        for (String sensitive : Set.of("555", "example.test", "Street", "Avenue", "Condition"))
            assertFalse(metadata.contains(sensitive), "entity audit leaked sensitive value: " + sensitive);
    }

    private static Connection recordingConnection(List<Execution> executions, String failTable) {
        return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[]{Connection.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "prepareStatement" -> statement((String) args[0], executions, failTable);
                    case "toString" -> "new-intake-test-connection";
                    case "isClosed" -> false;
                    default -> defaultValue(method.getReturnType());
                });
    }

    private static Connection transactionalConnection(List<String> calls, String failTable) {
        Connection delegate = recordingConnection(new ArrayList<>(), failTable);
        return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[]{Connection.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getAutoCommit" -> true;
                    case "setAutoCommit" -> { calls.add("setAutoCommit:" + args[0]); yield null; }
                    case "commit", "rollback", "close" -> { calls.add(method.getName()); yield null; }
                    case "prepareStatement" -> delegate.prepareStatement((String) args[0]);
                    case "isClosed" -> false;
                    default -> defaultValue(method.getReturnType());
                });
    }

    private static PreparedStatement statement(String sql, List<Execution> executions, String failTable) {
        Map<Integer, Object> bindings = new LinkedHashMap<>();
        return (PreparedStatement) Proxy.newProxyInstance(PreparedStatement.class.getClassLoader(),
                new Class<?>[]{PreparedStatement.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "setInt", "setLong", "setString", "setNull", "setBoolean", "setTimestamp", "setDate" -> {
                        bindings.put((Integer) args[0], "setNull".equals(method.getName()) ? null : args[1]); yield null;
                    }
                    case "executeQuery" -> {
                        executions.add(new Execution(sql,
                                Collections.unmodifiableMap(new LinkedHashMap<>(bindings))));
                        if (failTable != null && sql.contains(failTable)) throw new SQLException("structured write failed");
                        yield resultSet();
                    }
                    case "executeUpdate" -> {
                        executions.add(new Execution(sql,
                                Collections.unmodifiableMap(new LinkedHashMap<>(bindings))));
                        if (failTable != null && sql.contains(failTable)) throw new SQLException("audit write failed");
                        yield 1;
                    }
                    case "close" -> null;
                    default -> defaultValue(method.getReturnType());
                });
    }

    private static ResultSet resultSet() {
        int[] calls = {0};
        return (ResultSet) Proxy.newProxyInstance(ResultSet.class.getClassLoader(), new Class<?>[]{ResultSet.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "next" -> calls[0]++ == 0;
                    case "getInt" -> 1001;
                    case "getLong" -> 1001L;
                    case "close" -> null;
                    default -> defaultValue(method.getReturnType());
                });
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0F;
        if (type == double.class) return 0D;
        if (type == char.class) return '\0';
        return null;
    }

    private static String extractMethod(String source, String signature) {
        int start = source.indexOf(signature);
        assertTrue(start >= 0, "method signature not found: " + signature);
        int open = source.indexOf('{', start);
        assertTrue(open >= 0, "method body not found: " + signature);
        int depth = 0;
        for (int i = open; i < source.length(); i++) {
            if (source.charAt(i) == '{') depth++;
            else if (source.charAt(i) == '}' && --depth == 0) return source.substring(start, i + 1);
        }
        fail("unterminated method body: " + signature);
        return "";
    }

    private static CaseDao.NewIntakeCreateRequest request() {
        return new CaseDao.NewIntakeCreateRequest(7, "Intake", LocalDate.of(2026, 9, 1), LocalTime.NOON,
                false, 1, 2, "description", "summary", null, null, null, null, null,
                "Client", "Person", "101 Client Street", "(555) 101-0001", "client@example.test",
                LocalDate.of(1984, 2, 3), true, "Client condition", false,
                "Caller", "Person", "(555) 202-0002", "202 Caller Avenue", "caller@example.test",
                List.of(), 9, 1L, new byte[]{1}, List.of());
    }
}
