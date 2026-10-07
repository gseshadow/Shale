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

        List<Execution> writes = executions.stream().filter(e -> e.sql().startsWith("INSERT dbo.") && !isEntityAudit(e)).toList();
        assertEquals(6, writes.size());
        assertPoint(writes.get(0), "ContactPhoneNumbers", 101, "MOBILE", "(303) 555-0123", "+13035550123");
        assertPoint(writes.get(1), "ContactEmailAddresses", 101, "PERSONAL", "client@example.test", "client@example.test");
        assertPoint(writes.get(2), "ContactAddresses", 101, "HOME", "101 Client Street", null);
        assertPoint(writes.get(3), "ContactPhoneNumbers", 202, "MOBILE", "(720) 555-0123", "+17205550123");
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
                                "303-555-0123", "client@example.test", "101 Client Street");
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
                        "303-555-0123", "client@example.test", "101 Client Street");
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
        assertEquals(execution.sql().chars().filter(c->c=='?').count(),execution.bindings().size(),"nullable values must retain one binding per SQL column");
        if(table.equals("ContactPhoneNumbers"))assertNull(execution.bindings().get(6),"main number excludes the optional extension");
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

    private static PreparedStatement statement(String sql, List<Execution> executions, String failTable) {return statement(sql,executions,failTable,0,0);}
    private static PreparedStatement statement(String sql, List<Execution> executions, String failTable,int activeCount,int nextOrder) {
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
                        yield resultSet(sql,activeCount,nextOrder);
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

    private static ResultSet resultSet(String sql,int activeCount,int nextOrder) {
        int[] calls = {0};
        return (ResultSet) Proxy.newProxyInstance(ResultSet.class.getClassLoader(), new Class<?>[]{ResultSet.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "next" -> calls[0]++ == 0;
                    case "getInt" -> sql.startsWith("SELECT COUNT(*)")?((Integer)args[0]==1?activeCount:nextOrder):1001;
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

    @Test void mergePointInsertionUsesExistingOrderAndDoesNotCreateSecondPrimary()throws Exception{
        List<Execution> executions=new ArrayList<>();
        Connection connection=(Connection)Proxy.newProxyInstance(Connection.class.getClassLoader(),new Class<?>[]{Connection.class},(p,m,a)->m.getName().equals("prepareStatement")?statement((String)a[0],executions,null,2,2):defaultValue(m.getReturnType()));
        invokeContactPoints(dao(),connection,request(),101,"3035550123 x001",null,null);
        Execution point=executions.stream().filter(e->e.sql.startsWith("INSERT dbo.ContactPhoneNumbers")).findFirst().orElseThrow();
        assertEquals(false,point.bindings.get(7));assertEquals(2,point.bindings.get(8));assertEquals("001",point.bindings.get(6));assertEquals("+13035550123",point.bindings.get(5));
    }
    @Test void intakeProvenanceRequiresMatchingSessionTenantActorAndActiveAccount()throws Exception{
        Method guard=CaseDao.class.getDeclaredMethod("requireIntakeSession",Connection.class,CaseDao.NewIntakeCreateRequest.class);guard.setAccessible(true);
        for(int[] context:List.of(new int[]{7,9,1},new int[]{42,9,1},new int[]{7,10,1},new int[]{7,9,0})){
            Connection connection=(Connection)Proxy.newProxyInstance(Connection.class.getClassLoader(),new Class<?>[]{Connection.class},(p,m,a)->{
                if(!m.getName().equals("prepareStatement"))return defaultValue(m.getReturnType());String sql=(String)a[0];
                return Proxy.newProxyInstance(PreparedStatement.class.getClassLoader(),new Class<?>[]{PreparedStatement.class},(ps,method,args)->{
                    if(!method.getName().equals("executeQuery"))return defaultValue(method.getReturnType());boolean[] next={true};int value=sql.contains("PrincipalUserId")?context[1]:context[0];
                    return Proxy.newProxyInstance(ResultSet.class.getClassLoader(),new Class<?>[]{ResultSet.class},(rs,get,arguments)->switch(get.getName()){
                        case "next"->{boolean found=next[0]&&(!sql.contains("FROM dbo.Users")||context[2]==1);next[0]=false;yield found;}case "getObject","getInt"->value;default->defaultValue(get.getReturnType());});
                });
            });
            if(context[0]==7&&context[1]==9&&context[2]==1)assertDoesNotThrow(()->guard.invoke(null,connection,request()));
            else {InvocationTargetException failure=assertThrows(InvocationTargetException.class,()->guard.invoke(null,connection,request()));assertInstanceOf(SecurityException.class,failure.getCause());}
        }
    }

    private static CaseDao.NewIntakeCreateRequest withValues(Map<String,Object> replacements)throws Exception {
        var components=CaseDao.NewIntakeCreateRequest.class.getRecordComponents();
        Object[] values=new Object[components.length];Class<?>[] types=new Class<?>[components.length];
        var original=request();
        for(int i=0;i<components.length;i++){types[i]=components[i].getType();values[i]=replacements.containsKey(components[i].getName())?replacements.get(components[i].getName()):components[i].getAccessor().invoke(original);}
        return CaseDao.NewIntakeCreateRequest.class.getDeclaredConstructor(types).newInstance(values);
    }
    @Test void requiredPhonesAndIndependentUnavailableReasonsAreAuthoritative()throws Exception{
        assertThrows(IllegalArgumentException.class,()->CaseDao.validateIntakeContactValues(withValues(Map.of("clientPhone",""))));
        assertDoesNotThrow(()->CaseDao.validateIntakeContactValues(withValues(Map.of("clientPhone","","clientPhoneUnavailableReason",com.shale.core.validation.PhoneUnavailableReason.UNKNOWN))));
        assertThrows(IllegalArgumentException.class,()->CaseDao.validateIntakeContactValues(withValues(Map.of("clientPhone","","clientPhoneUnavailableReason",com.shale.core.validation.PhoneUnavailableReason.UNKNOWN,"callerPhone",""))));
        assertDoesNotThrow(()->CaseDao.validateIntakeContactValues(withValues(Map.of("clientPhone","","clientPhoneUnavailableReason",com.shale.core.validation.PhoneUnavailableReason.UNKNOWN,"callerPhone","","callerPhoneUnavailableReason",com.shale.core.validation.PhoneUnavailableReason.NO_PHONE))));
        assertThrows(IllegalArgumentException.class,()->CaseDao.validateIntakeContactValues(withValues(Map.of("clientPhone","0","clientPhoneUnavailableReason",com.shale.core.validation.PhoneUnavailableReason.NOT_PROVIDED))));
        assertDoesNotThrow(()->CaseDao.validateIntakeContactValues(withValues(Map.of("callerIsClient",true,"callerPhone","0"))));
        assertThrows(IllegalArgumentException.class,()->CaseDao.validateIntakeContactValues(withValues(Map.of("clientPhoneExtension","ABC"))));
    }
    @Test void availabilityObservationsCopyClientReasonOnlyWhenCallerIsClient()throws Exception{
        var method=CaseDao.class.getDeclaredMethod("recordPhoneAvailability",Connection.class,CaseDao.NewIntakeCreateRequest.class,long.class,int.class,int.class);method.setAccessible(true);
        List<Execution> writes=new ArrayList<>();var request=withValues(Map.of("callerIsClient",true,"clientPhone","","clientPhoneUnavailableReason",com.shale.core.validation.PhoneUnavailableReason.UNKNOWN));
        method.invoke(null,recordingConnection(writes,null),request,10L,101,101);
        assertEquals(2,writes.size());assertEquals("CLIENT",writes.get(0).bindings.get(4));assertEquals("CALLER",writes.get(1).bindings.get(4));
        for(var write:writes){assertEquals("UNKNOWN",write.bindings.get(5));assertEquals(7,write.bindings.get(1));assertEquals(101,write.bindings.get(3));assertEquals(9,write.bindings.get(6));}
    }

    @Test void intakeDirectWriterExtractsAndFormatsApprovedUsLengthsAndExtensions() throws Exception {
        for(String input:List.of("Call: (505) 903 3568 x001","(903) 3568 x001","1-505-903-3568 x001")) {
            List<Execution> writes=new ArrayList<>();
            invokeContactPoints(dao(),recordingConnection(writes,null),request(),101,input,null,null);
            var phone=writes.stream().filter(write->write.sql.startsWith("INSERT dbo.ContactPhoneNumbers")).findFirst().orElseThrow();
            var expected=com.shale.data.validation.ContactValues.INSTANCE.phone(input,null,true,"phone");
            assertEquals(expected.displayInput(),phone.bindings.get(4));assertEquals(expected.normalizedNumber(),phone.bindings.get(5));assertEquals("001",phone.bindings.get(6));
        }
        assertDoesNotThrow(()->CaseDao.validateIntakeContactValues(withValues(Map.of("clientPhone","Call: 9033568","callerPhone","1-505-903-3568"))));
        assertThrows(IllegalArgumentException.class,()->CaseDao.validateIntakeContactValues(withValues(Map.of("callerPhone","2-505-903-3568"))));
    }

    private static CaseDao.NewIntakeCreateRequest request() {
        return new CaseDao.NewIntakeCreateRequest(7, "Intake", LocalDate.of(2026, 9, 1), LocalTime.NOON,
                false, 1, 2, "description", "summary", null, null, null, null, null,
                "Client", "Person", "101 Client Street", "(303) 555-0123", "client@example.test",
                LocalDate.of(1984, 2, 3), true, "Client condition", false,
                "Caller", "Person", "(720) 555-0123", "202 Caller Avenue", "caller@example.test",
                List.of(), 9, 1L, new byte[]{1}, List.of());
    }
    @Test void localIntakeCreateAndMergePersistWholeNormalizedNumberAndExtensions()throws Exception {
        var request=withValues(Map.of("clientPhone","555-0123 x001","callerPhone","234 5678"));
        assertDoesNotThrow(()->CaseDao.validateIntakeContactValues(request));
        List<Execution> writes=new ArrayList<>();
        invokeContactPoints(dao(),recordingConnection(writes,null),request,101,request.clientPhone(),null,null);
        Execution point=writes.stream().filter(e->e.sql.startsWith("INSERT dbo.ContactPhoneNumbers")).findFirst().orElseThrow();
        assertEquals("555-0123",point.bindings.get(4));assertEquals("5550123",point.bindings.get(5));assertEquals("001",point.bindings.get(6));
        assertEquals(point.sql.chars().filter(c->c=='?').count(),point.bindings.size(),"all SQL parameters must be bound");
        writes.clear();
        Method merge=CaseDao.class.getDeclaredMethod("insertMissingContactPoints",Connection.class,CaseDao.NewIntakeCreateRequest.class,int.class,String.class,String.class,String.class);merge.setAccessible(true);
        merge.invoke(dao(),recordingConnection(writes,null),request,101,request.clientPhone(),null,null);
        Execution lookup=writes.stream().filter(e->e.sql.contains("LOWER(LTRIM(RTRIM(NormalizedNumber)))")).findFirst().orElseThrow();
        assertEquals("5550123",lookup.bindings.get(3));assertEquals("001",lookup.bindings.get(4));
        assertFalse(lookup.sql.contains("RIGHT("),"local and full numbers must never be equated by suffix");
    }

}
