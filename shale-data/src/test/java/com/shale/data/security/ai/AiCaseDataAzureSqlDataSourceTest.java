package com.shale.data.security.ai;

import com.microsoft.sqlserver.jdbc.SQLServerDataSource;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.net.SocketTimeoutException;
import java.util.*;
import java.util.logging.Logger;
import static org.junit.jupiter.api.Assertions.*;

/** Executes the pinned Microsoft driver's URL/property merge offline, not an Azure socket claim.
 * Live setup explicitly establishes and verifies its connected socket guard after login. */
class AiCaseDataAzureSqlDataSourceTest {
    private static Map<String,String> settings() {
        var env=AiCaseDataAzureSqlAcceptanceSafetyTest.settings();
        env.put("SHALE_AZURE_OWNER_USERNAME","synthetic_owner");env.put("SHALE_AZURE_OWNER_PASSWORD","synthetic-owner-only");return env;
    }
    @Test void originalConstructionOrderReallyOverridesFiveSecondsInMicrosoftDriver()throws Exception {
        var source=AiCaseDataAzureSqlDatabaseReadOnlyTest.source(settings(),"OWNER");
        assertEquals("5000",effective(source).getProperty("socketTimeout"));
        source.setSocketTimeout(120_000);
        assertEquals(120_000,source.getSocketTimeout());
        assertEquals("120000",effective(source).getProperty("socketTimeout"),"A five-second Azure observation alone does not prove the later setter was ignored");
    }
    @Test void separateOwnerSourceHasBoundedSetupPropertiesWithoutChangingReadonlySource()throws Exception {
        var owner=AiCaseDataAzureSqlDisposableSetupTest.ownerSource(settings());var properties=effective(owner);
        assertEquals("120000",properties.getProperty("socketTimeout"));assertEquals("30",properties.getProperty("loginTimeout"));
        assertEquals("20",properties.getProperty("queryTimeout"));assertEquals("5",properties.getProperty("cancelQueryTimeout"));
        assertEquals("true",properties.getProperty("encrypt"));assertEquals("false",properties.getProperty("trustServerCertificate"));
        assertEquals(settings().get("SHALE_AZURE_DATABASE"),properties.getProperty("databaseName"));
        assertEquals(settings().get("SHALE_AZURE_OWNER_USERNAME"),properties.getProperty("user"));
        assertEquals(settings().get("SHALE_AZURE_OWNER_PASSWORD"),properties.getProperty("password"));
        assertFalse(properties.containsKey("accessToken"));assertFalse(properties.containsKey("authentication"));
        assertFalse(properties.containsKey("integratedSecurity"));assertFalse(owner.getURL().contains("Password"));
        var read=AiCaseDataAzureSqlDatabaseReadOnlyTest.source(settings(),"AI");
        assertEquals("5000",effective(read).getProperty("socketTimeout"));assertEquals("3",effective(read).getProperty("loginTimeout"));
        assertEquals("true",effective(read).getProperty("encrypt"));assertEquals("false",effective(read).getProperty("trustServerCertificate"));
    }
    @Test void connectedTimeoutMismatchStopsBeforeAnyPhaseWork()throws Exception {
        var executed=new boolean[1];
        assertThrows(IllegalStateException.class,()->AiCaseDataAzureSqlDisposableSetupTest.phase(connection(5000),AiCaseDataAzureSqlDisposableSetupTest.Phase.STAGE0_BEFORE,()->executed[0]=true));
        assertFalse(executed[0]);
        assertDoesNotThrow(()->AiCaseDataAzureSqlDisposableSetupTest.requireOwnerNetworkTimeout(connection(120_000)));
    }
    @Test void failedPhaseMarkersNeverEchoExceptionsOrSql()throws Exception {
        var bytes=new java.io.ByteArrayOutputStream();var previous=System.err;
        try(var out=new java.io.PrintStream(bytes)) {
            System.setErr(out);
            assertThrows(IllegalStateException.class,()->AiCaseDataAzureSqlDisposableSetupTest.phase(connection(120_000),AiCaseDataAzureSqlDisposableSetupTest.Phase.STAGE0_BEFORE,()->{throw new IllegalStateException("private-sql-secret-sid");}));
        }finally{System.setErr(previous);}
        String log=bytes.toString(java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(log.contains("phase=STAGE0_BEFORE status=BEGIN"));assertTrue(log.contains("phase=STAGE0_BEFORE status=FAIL"));assertFalse(log.contains("private"));
    }
    @Test void ownerLoginFailureHasSanitizedBoundsAndNoThrowableChain()throws Exception {
        var failure=new SQLException("jdbc:sqlserver://private-server;databaseName=private-db password=private-secret schema SID token", "08S01",0);
        failure.initCause(new SocketTimeoutException("private-driver-message"));
        assertSanitizedConnectFailure(failure,"sqlState=08S01","rootClass=java.net.SocketTimeoutException");
        assertSanitizedConnectFailure(new SQLException("private", "private-state\nINJECT",18456),"sqlState=unavailable","sqlCode=18456");
        assertSanitizedConnectFailure(new IllegalArgumentException("private"),"sqlState=unavailable","sqlCode=0");
    }
    private static void assertSanitizedConnectFailure(Exception failure,String... expected)throws Exception {
        var bytes=new java.io.ByteArrayOutputStream();var previous=System.err;
        AiCaseDataAzureSqlDisposableSetupTest.OwnerConnection source=()->{
            if(failure instanceof SQLException sql)throw sql;
            throw (RuntimeException)failure;
        };
        IllegalStateException sanitized;
        try(var out=new java.io.PrintStream(bytes)) {
            System.setErr(out);
            sanitized=assertThrows(IllegalStateException.class,()->AiCaseDataAzureSqlDisposableSetupTest.ownerConnect(source));
            sanitized.printStackTrace(out); // Surefire-style output must also be safe.
        }finally{System.setErr(previous);}
        String log=bytes.toString(java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(log.contains("phase=OWNER_CONNECT status=BEGIN"));
        assertTrue(log.matches("(?s).*phase=OWNER_CONNECT status=FAIL elapsedMillis=[0-9]+.*"));
        for(String field:List.of("loginSeconds=30","socketMillis=120000","querySeconds=20","cancelSeconds=5"))assertTrue(log.contains(field));
        for(String field:expected)assertTrue(log.contains(field),field);
        assertFalse(log.contains("private"));assertFalse(log.contains("jdbc:"));assertFalse(log.contains("INJECT"));
        assertFalse(log.contains("status=PASS"));assertFalse(log.contains("phase=OWNER_PREFLIGHT"));
        assertNull(sanitized.getCause());assertEquals(0,sanitized.getSuppressed().length);
    }
    @Test void enabledDriverLoggingRefusesOwnerConnectionBeforeNetwork() {
        var logger=Logger.getLogger("com.microsoft.sqlserver.jdbc.internals.offlineProbe");var previous=logger.getLevel();
        var opened=new boolean[1];
        var source=new SQLServerDataSource(){@Override public Connection getConnection(){opened[0]=true;throw new AssertionError("Must not open");}};
        try {
            logger.setLevel(java.util.logging.Level.ALL);
            assertThrows(IllegalStateException.class,()->AiCaseDataAzureSqlDisposableSetupTest.ownerConnect(source));
            assertFalse(opened[0]);
        }finally{logger.setLevel(previous);}
    }
    @Test void ownerConnectEstablishesAndVerifiesConnectedGuardBeforeReturning()throws Exception {
        var timeout=new int[]{14_900};var closed=new boolean[1];var operations=new ArrayList<String>();
        Connection c=(Connection)Proxy.newProxyInstance(Connection.class.getClassLoader(),new Class<?>[]{Connection.class},(p,m,a)->{
            operations.add(m.getName());
            return switch(m.getName()) {
                case "setNetworkTimeout"->{assertNull(a[0]);timeout[0]=(int)a[1];yield null;}
                case "getNetworkTimeout"->timeout[0];
                case "getMetaData"->Proxy.newProxyInstance(Connection.class.getClassLoader(),new Class<?>[]{java.sql.DatabaseMetaData.class},(mp,mm,ma)->{
                    assertEquals("getDriverVersion",mm.getName());return "12.6.1.0";
                });
                case "close"->{closed[0]=true;yield null;}
                default->throw new UnsupportedOperationException("No metadata/SQL before connected guard");
            };
        });
        AiCaseDataAzureSqlDisposableSetupTest.OwnerConnection source=()->c;
        assertSame(c,AiCaseDataAzureSqlDisposableSetupTest.ownerConnect(source));
        assertEquals(120_000,timeout[0]);assertEquals("setNetworkTimeout",operations.getFirst());assertFalse(closed[0]);
    }
    @Test void failedConnectedGuardClosesConnectionAndNeverReturnsIt()throws Exception {
        var closed=new boolean[1];
        Connection c=(Connection)Proxy.newProxyInstance(Connection.class.getClassLoader(),new Class<?>[]{Connection.class},(p,m,a)->switch(m.getName()) {
            case "setNetworkTimeout"->null; // Simulate a driver that does not apply the guard.
            case "getNetworkTimeout"->5000;
            case "close"->{closed[0]=true;throw new SQLException("private-close-secret");}
            default->throw new UnsupportedOperationException("No SQL after a failed guard");
        });
        AiCaseDataAzureSqlDisposableSetupTest.OwnerConnection source=()->c;
        var failure=assertThrows(IllegalStateException.class,()->AiCaseDataAzureSqlDisposableSetupTest.ownerConnect(source));
        assertTrue(closed[0]);assertNull(failure.getCause());assertEquals(0,failure.getSuppressed().length);
        assertFalse(failure.toString().contains("private"));
    }
    @Test void ownerPreflightOnlyReadsCatalogsAndRefusesExistingObjectsOrWrongTarget()throws Exception {
        var queries=new ArrayList<String>();
        assertDoesNotThrow(()->AiCaseDataAzureSqlDisposableSetupTest.ownerPreflight(catalogConnection(queries,"synthetic-db",0),"synthetic-db"));
        assertEquals(2,queries.size(),"Only edition and owner/empty-catalog reads belong in the probe");
        assertThrows(AssertionError.class,()->AiCaseDataAzureSqlDisposableSetupTest.ownerPreflight(catalogConnection(new ArrayList<>(),"synthetic-db",1),"synthetic-db"));
        var failure=assertThrows(AssertionError.class,()->AiCaseDataAzureSqlDisposableSetupTest.ownerPreflight(catalogConnection(new ArrayList<>(),"private-target",0),"synthetic-db"));
        assertFalse(failure.toString().contains("private-target"));
    }
    private static Connection catalogConnection(List<String> queries,String db,int objects) {
        return (Connection)Proxy.newProxyInstance(Connection.class.getClassLoader(),new Class<?>[]{Connection.class},(p,m,a)->{
            assertEquals("createStatement",m.getName(),"Probe must not perform any non-catalog operation");
            return Proxy.newProxyInstance(Connection.class.getClassLoader(),new Class<?>[]{java.sql.Statement.class},(sp,sm,sa)->switch(sm.getName()) {
                case "setQueryTimeout","close"->null;
                case "executeQuery"->{
                    String sql=(String)sa[0];assertTrue(sql.startsWith("SELECT "),"No mutating statement belongs in owner preflight");queries.add(sql);
                    boolean edition=sql.contains("EngineEdition");var row=new boolean[1];
                    yield Proxy.newProxyInstance(Connection.class.getClassLoader(),new Class<?>[]{java.sql.ResultSet.class},(rp,rm,ra)->switch(rm.getName()) {
                        case "next"->{boolean first=!row[0];row[0]=true;yield first;}
                        case "getInt"->edition?5:((int)ra[0]==3?objects:0);
                        case "getString"->(int)ra[0]==1?db:"dbo";
                        case "wasNull"->false;
                        case "close"->null;
                        default->throw new AssertionError("Unexpected preflight result access");
                    });
                }
                default->throw new AssertionError("Probe attempted a non-read operation");
            });
        });
    }
    private static Connection connection(int timeout) {
        return (Connection)Proxy.newProxyInstance(Connection.class.getClassLoader(),new Class<?>[]{Connection.class},(p,m,a)->{
            if(m.getName().equals("getNetworkTimeout"))return timeout;
            throw new UnsupportedOperationException("Unexpected connection operation in timeout guard test");
        });
    }
    private static Properties effective(SQLServerDataSource source)throws Exception {
        // These are the actual parser/merge methods called by SQLServerDataSource.getConnectionInternal
        // in the pinned driver. Synthetic settings only; never inspect live credential properties.
        var field=SQLServerDataSource.class.getDeclaredField("connectionProps");field.setAccessible(true);
        var parse=Class.forName("com.microsoft.sqlserver.jdbc.Util").getDeclaredMethod("parseUrl",String.class,Logger.class);parse.setAccessible(true);
        var merge=Class.forName("com.microsoft.sqlserver.jdbc.SQLServerDriver").getDeclaredMethod("mergeURLAndSuppliedProperties",Properties.class,Properties.class);merge.setAccessible(true);
        return (Properties)merge.invoke(null,parse.invoke(null,source.getURL(),Logger.getAnonymousLogger()),field.get(source));
    }
}
