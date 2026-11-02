package com.shale.data.security.ai;

import org.junit.jupiter.api.*;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Owner-only fixture construction, separate from read-only acceptance and production startup.
 * Reuses authoritative DDL safety and the existing V2 Phase1R preparation unchanged. */
class AiCaseDataAzureSqlDisposableSetupTest {
    static final int OWNER_SOCKET_MILLIS=120_000,OWNER_LOGIN_SECONDS=30,OWNER_QUERY_SECONDS=20,OWNER_CANCEL_SECONDS=5;
    enum Phase { OWNER_CONNECT, OWNER_PREFLIGHT, STAGE0_BEFORE, SCHEMA_IMPORT, PREREQUISITE_MIGRATIONS,
        PHASE1R_AND_V2_PREREQUISITE, V2_INSTALL, CONTAINED_USERS, DEPLOYMENT_BINDING, CUMULATIVE_VERIFY, STAGE0_AFTER, OFF_CHECK }
    @FunctionalInterface interface Work { void run()throws Exception; }
    @Test @Timeout(300) void provisionEmptyOwnedAzureFixture()throws Exception {
        Assumptions.assumeTrue("CREATE_OWNED_DISPOSABLE_AZURE_FIXTURE".equals(System.getenv("SHALE_AZURE_SETUP_OPT_IN")),"Separate mutating setup opt-in required");
        requireQuietDriverLogging(); // Before datasource setters can log target/credentials.
        String db=validateOwner(System.getenv());
        String hash="4b64b50da531ebc6a2eb9dc26c6555ef555621df371fe9d8a5cf994fb6e8417f";
        assertEquals(hash,System.getenv("SHALE_CASE_VERIFY_SCHEMA_SHA256"));
        var safety=Class.forName("com.shale.data.verification.CaseReadVerificationSafety");
        var parser=safety.getDeclaredMethod("schema",Path.class,String.class);parser.setAccessible(true);
        @SuppressWarnings("unchecked") var schema=(List<String>)parser.invoke(null,Path.of(Objects.requireNonNull(System.getenv("SHALE_CASE_VERIFY_SCHEMA"))),hash);
        var owner=ownerSource(System.getenv());
        var sequence=new ArrayList<String>();
        try(var c=ownerConnect(owner)) {
            phase(c,Phase.OWNER_PREFLIGHT,()->ownerPreflight(c,db));
            phase(c,Phase.STAGE0_BEFORE,()->execute(c,Files.readString(Path.of("../docs/sql/ai-integration/azure-sql-stage0-verify.sql"))));
            sequence.add("operator Stage 0: seven finding counts zero");
            phase(c,Phase.SCHEMA_IMPORT,()->{for(String batch:schema)execute(c,batch);});sequence.add("private authoritative candidate SHA256="+hash);
            phase(c,Phase.PREREQUISITE_MIGRATIONS,()->{
            for(String migration:List.of("phase3b1.sql","phase3b2b.sql","phase3b2c1.sql","phase3b2c2.sql","revocation.sql","phase3b2d.sql","phase3b3.sql")) {
                execute(c,Files.readString(Path.of("../docs/sql/ai-integration/"+migration)));sequence.add(migration);
            }
            });
            // Existing clean-room V2 preparation removes only the candidate's three filters and
            // uses guarded Phase1R PREFLIGHT/APPLY/POSTCHECK. Reviewed hashes come from this private fixture.
            phase(c,Phase.PHASE1R_AND_V2_PREREQUISITE,()->{var fixture=new AiCaseDataV2AuthoritativeLocalSqlServerTest();fixture.db=db;fixture.beforeConfinement(c);});
            sequence.add("existing authoritative V2 beforeConfinement: Phase1R PREFLIGHT/APPLY/POSTCHECK + V2 prerequisite");
            phase(c,Phase.V2_INSTALL,()->AzureOwnerV2Install.install(c,Files.readAllBytes(Path.of("../docs/sql/ai-integration/phase3b8-confinement.sql"))));sequence.add("phase3b8-confinement.sql");
            phase(c,Phase.CONTAINED_USERS,()->{
            for(String identity:AiCaseDataAzureSqlDatabaseReadOnlyTest.IDENTITIES) {
                // CREATE USER requires a literal. Never place it in shell arguments/logs; redact failure.
                try {execute(c,"CREATE USER ["+required(identity+"_USERNAME")+"] WITH PASSWORD='"+required(identity+"_PASSWORD").replace("'","''")+"'");}
                catch(SQLException failure){throw new IllegalStateException("Contained-user setup failed; SQL code="+failure.getErrorCode());}
            }
            execute(c,"ALTER ROLE AiCaseDataRuntime ADD MEMBER ["+required("AI_USERNAME")+"]");
            execute(c,"ALTER ROLE AiCaseDataCatalogVerifier ADD MEMBER ["+required("VERIFIER_USERNAME")+"]");
            execute(c,"ALTER ROLE AiIntegrationBootstrap ADD MEMBER ["+required("BOOTSTRAP_USERNAME")+"]");
            execute(c,"ALTER ROLE AiIntegrationRuntime ADD MEMBER ["+required("RUNTIME_USERNAME")+"]");
            });
            phase(c,Phase.DEPLOYMENT_BINDING,()->{
            try(var p=c.prepareStatement("INSERT dbo.AiCaseDataDeployment VALUES(1,?,?,'case-read-v1',?,'UTC')")) {
                p.setQueryTimeout(20);p.setString(1,required("ISSUER"));p.setString(2,required("RESOURCE"));p.setString(3,java.time.ZoneId.systemDefault().getId());p.executeUpdate();
            }
            });
            phase(c,Phase.CUMULATIVE_VERIFY,()->execute(c,Files.readString(Path.of("../docs/sql/ai-integration/verify.sql"))));sequence.add("contained users + exact memberships + reserved immutable binding + cumulative verify.sql");
            phase(c,Phase.STAGE0_AFTER,()->execute(c,Files.readString(Path.of("../docs/sql/ai-integration/azure-sql-stage0-verify.sql"))));sequence.add("operator Stage 0 AFTER provisioning: seven finding counts zero");
            phase(c,Phase.OFF_CHECK,()->{
            try(var s=c.createStatement();var r=s.executeQuery("SELECT (SELECT COUNT(*) FROM dbo.AiProductCapability WHERE Enabled=1)+(SELECT COUNT(*) FROM dbo.TenantAiIntegrationPolicy)+(SELECT COUNT(*) FROM dbo.AiIntegrationRegistration)+(SELECT COUNT(*) FROM dbo.AiDelegatedSession)+(SELECT COUNT(*) FROM dbo.AiOAuthCredential)")) {
                assertTrue(r.next());assertEquals(0,r.getInt(1),"Capabilities and integration authority must remain absent/OFF");
            }
            });
        } catch(Exception|AssertionError failure) {
            // Includes resource-close failures; do not let Surefire print private SQL/targets.
            throw new IllegalStateException("Owned disposable setup failed; discard owned scope; no retry/adoption");
        }
        Path output=Path.of("../work/azure-native/setup-sequence.txt");Files.createDirectories(output.getParent());Files.write(output,sequence);
    }
    static com.microsoft.sqlserver.jdbc.SQLServerDataSource ownerSource(Map<String,String> env)throws SQLException {
        AiCaseDataAzureSqlDatabaseReadOnlyTest.validate(env);
        var owner=new com.microsoft.sqlserver.jdbc.SQLServerDataSource();
        owner.setURL(AiCaseDataAzureSqlDatabaseReadOnlyTest.setting(env,"JDBC_URL"));
        owner.setUser(AiCaseDataAzureSqlDatabaseReadOnlyTest.setting(env,"OWNER_USERNAME"));
        owner.setPassword(AiCaseDataAzureSqlDatabaseReadOnlyTest.setting(env,"OWNER_PASSWORD"));
        owner.setEncrypt("true");owner.setTrustServerCertificate(false);
        owner.setLoginTimeout(OWNER_LOGIN_SECONDS);owner.setSocketTimeout(OWNER_SOCKET_MILLIS);
        owner.setQueryTimeout(OWNER_QUERY_SECONDS);owner.setCancelQueryTimeout(OWNER_CANCEL_SECONDS);
        return owner;
    }
    static String validateOwner(Map<String,String> env) {
        AiCaseDataAzureSqlDatabaseReadOnlyTest.validate(env);
        String db=AiCaseDataAzureSqlDatabaseReadOnlyTest.setting(env,"DATABASE");
        assertTrue(db.equals(AiCaseDataAzureSqlDatabaseReadOnlyTest.setting(env,"OWNED_DATABASE_ACK")),"Owned disposable acknowledgement mismatch");
        for(String identity:AiCaseDataAzureSqlDatabaseReadOnlyTest.IDENTITIES) {
            assertFalse(AiCaseDataAzureSqlDatabaseReadOnlyTest.setting(env,"OWNER_USERNAME").equalsIgnoreCase(AiCaseDataAzureSqlDatabaseReadOnlyTest.setting(env,identity+"_USERNAME")),"Owner identity must be independent");
            assertFalse(AiCaseDataAzureSqlDatabaseReadOnlyTest.setting(env,"OWNER_PASSWORD").equals(AiCaseDataAzureSqlDatabaseReadOnlyTest.setting(env,identity+"_PASSWORD")),"Owner credential must be independent");
        }
        return db;
    }
    @FunctionalInterface interface OwnerConnection { Connection open()throws SQLException; }
    static Connection ownerConnect(com.microsoft.sqlserver.jdbc.SQLServerDataSource owner) {
        requireQuietDriverLogging();
        return ownerConnect(owner::getConnection);
    }
    static void requireQuietDriverLogging() {
        // JDBC uses JUL independently of Logback. Fail before network access if a
        // driver logger could print target/authentication text during getConnection.
        String namespace="com.microsoft.sqlserver.jdbc";
        var root=java.util.logging.Logger.getLogger(namespace);
        if(root.isLoggable(java.util.logging.Level.SEVERE))throw new IllegalStateException("Quiet JDBC JUL configuration required before owner connection");
        var manager=java.util.logging.LogManager.getLogManager();
        for(String name:Collections.list(manager.getLoggerNames()))if(name.startsWith(namespace+".")) {
            var logger=manager.getLogger(name);
            if(logger!=null&&logger.isLoggable(java.util.logging.Level.SEVERE))throw new IllegalStateException("JDBC tracing must be disabled before owner connection");
        }
    }
    static Connection ownerConnect(OwnerConnection owner) {
        long started=System.nanoTime();
        String bounds=String.format(Locale.ROOT,"loginSeconds=%d socketMillis=%d querySeconds=%d cancelSeconds=%d",
                OWNER_LOGIN_SECONDS,OWNER_SOCKET_MILLIS,OWNER_QUERY_SECONDS,OWNER_CANCEL_SECONDS);
        System.err.printf("AZURE_SETUP phase=OWNER_CONNECT status=BEGIN elapsedMillis=0 %s%n",bounds);
        Connection c=null;
        try {
            c=owner.open();
            // 12.6.1 clamps SO_TIMEOUT to the remaining login budget at channel open and
            // does not restore socketTimeout on login completion. Establish the connected
            // guard explicitly, before any metadata/preflight/SQL, then verify it.
            c.setNetworkTimeout(null,OWNER_SOCKET_MILLIS);
            requireOwnerNetworkTimeout(c);
            String driver=c.getMetaData().getDriverVersion();
            if(driver==null||!driver.matches("[0-9.]+"))driver="unavailable";
            System.err.printf("AZURE_SETUP bounds %s driver=%s%n",bounds,driver);
            System.err.printf("AZURE_SETUP phase=OWNER_CONNECT status=PASS elapsedMillis=%d %s connectedSocketMillis=%d%n",
                    (System.nanoTime()-started)/1_000_000,bounds,c.getNetworkTimeout());
            return c;
        } catch(SQLException|RuntimeException failure) {
            if(c!=null)try{c.close();}catch(SQLException|RuntimeException ignored){/* Never expose cleanup exception text. */}
            String state=failure instanceof SQLException sql?sql.getSQLState():null;
            String safeState=state!=null&&state.matches("[A-Z0-9]{5}")?state:"unavailable";
            int code=failure instanceof SQLException sql?sql.getErrorCode():0;
            Throwable root=failure;
            for(int i=0;i<8&&root.getCause()!=null&&root.getCause()!=root;i++)root=root.getCause();
            String diagnostic=String.format(Locale.ROOT,
                    "phase=OWNER_CONNECT status=FAIL elapsedMillis=%d %s exceptionClass=%s rootClass=%s sqlState=%s sqlCode=%d",
                    (System.nanoTime()-started)/1_000_000,bounds,failure.getClass().getName(),root.getClass().getName(),safeState,code);
            System.err.println("AZURE_SETUP "+diagnostic);
            // No cause/suppressed chain: Surefire would otherwise print driver messages,
            // URLs, target names or authentication details despite a sanitized marker.
            throw new IllegalStateException(diagnostic+"; discard fixture; no retry/adoption");
        }
    }
    static void ownerPreflight(Connection c,String db)throws SQLException {
        assertEquals(AiCaseDataAttestation.Platform.AZURE_SQL_DATABASE,AiCaseDataAttestation.platform(c));
        try(var s=c.createStatement()) {
            s.setQueryTimeout(OWNER_QUERY_SECONDS);
            try(var r=s.executeQuery("SELECT DB_NAME(),USER_NAME(),(SELECT COUNT(*) FROM sys.objects WHERE is_ms_shipped=0),(SELECT COUNT(*) FROM sys.database_principals WHERE principal_id>4 AND type<>'R')")) {
                assertTrue(r.next(),"Owner preflight evidence required");
                assertTrue(db.equals(r.getString(1)),"Owner target mismatch");
                assertTrue("dbo".equals(r.getString(2)),"Owner dbo required");
                assertEquals(0,r.getInt(3),"Only an empty new disposable database may be provisioned");
                assertEquals(0,r.getInt(4),"Existing custom identities forbid fixture adoption");
            }
        }
    }
    static void requireOwnerNetworkTimeout(Connection c)throws SQLException {
        if(c.getNetworkTimeout()!=OWNER_SOCKET_MILLIS)throw new IllegalStateException("Azure setup owner socket timeout mismatch; discard fixture, do not retry/adopt");
    }
    static void phase(Connection c,Phase phase,Work work)throws Exception {
        requireOwnerNetworkTimeout(c);
        marker(phase,"BEGIN",0);long start=System.nanoTime();boolean passed=false;
        try{work.run();passed=true;}finally{marker(phase,passed?"PASS":"FAIL",(System.nanoTime()-start)/1_000_000);}
    }
    private static void marker(Phase phase,String status,long millis){System.err.printf("AZURE_SETUP phase=%s status=%s elapsedMillis=%d%n",phase,status,millis);}
    private static String required(String suffix){return AiCaseDataAzureSqlDatabaseReadOnlyTest.required(suffix);}
    private static void execute(Connection c,String sql)throws SQLException{AiCaseDataLocalSqlServerTest.sql(c,sql);}
}
