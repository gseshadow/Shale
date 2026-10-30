package com.shale.data.security.ai;

import com.microsoft.sqlserver.jdbc.SQLServerDataSource;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.util.*;
import java.util.logging.Logger;
import static org.junit.jupiter.api.Assertions.*;

/** Executes the pinned Microsoft driver's URL/property merge offline, not an Azure socket claim.
 * Live setup separately refuses a connected socket whose JDBC network timeout differs. */
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
        assertEquals("120000",properties.getProperty("socketTimeout"));assertEquals("15",properties.getProperty("loginTimeout"));
        assertEquals("20",properties.getProperty("queryTimeout"));assertEquals("5",properties.getProperty("cancelQueryTimeout"));
        assertEquals("true",properties.getProperty("encrypt"));assertEquals("false",properties.getProperty("trustServerCertificate"));
        assertEquals(settings().get("SHALE_AZURE_DATABASE"),properties.getProperty("databaseName"));
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
