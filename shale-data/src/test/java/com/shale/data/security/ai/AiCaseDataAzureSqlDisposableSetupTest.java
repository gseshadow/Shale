package com.shale.data.security.ai;

import org.junit.jupiter.api.*;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Owner-only fixture construction, separate from read-only acceptance and production startup.
 * Reuses authoritative DDL safety and the existing V2 Phase1R preparation unchanged. */
class AiCaseDataAzureSqlDisposableSetupTest {
    static final int OWNER_SOCKET_MILLIS=120_000,OWNER_LOGIN_SECONDS=15,OWNER_QUERY_SECONDS=20,OWNER_CANCEL_SECONDS=5;
    enum Phase { OWNER_CONNECT, OWNER_PREFLIGHT, STAGE0_BEFORE, SCHEMA_IMPORT, PREREQUISITE_MIGRATIONS,
        PHASE1R_AND_V2_PREREQUISITE, V2_INSTALL, CONTAINED_USERS, DEPLOYMENT_BINDING, CUMULATIVE_VERIFY, STAGE0_AFTER, OFF_CHECK }
    @FunctionalInterface interface Work { void run()throws Exception; }
    @Test @Timeout(300) void provisionEmptyOwnedAzureFixture()throws Exception {
        Assumptions.assumeTrue("CREATE_OWNED_DISPOSABLE_AZURE_FIXTURE".equals(System.getenv("SHALE_AZURE_SETUP_OPT_IN")),"Separate mutating setup opt-in required");
        AiCaseDataAzureSqlDatabaseReadOnlyTest.validate(System.getenv());
        String db=required("DATABASE");assertEquals(db,required("OWNED_DATABASE_ACK"));
        for(String identity:AiCaseDataAzureSqlDatabaseReadOnlyTest.IDENTITIES) {
            assertNotEquals(required("OWNER_USERNAME").toLowerCase(Locale.ROOT),required(identity+"_USERNAME").toLowerCase(Locale.ROOT));
            assertNotEquals(required("OWNER_PASSWORD"),required(identity+"_PASSWORD"),"Owner credentials must not be reused");
        }
        String hash="4b64b50da531ebc6a2eb9dc26c6555ef555621df371fe9d8a5cf994fb6e8417f";
        assertEquals(hash,System.getenv("SHALE_CASE_VERIFY_SCHEMA_SHA256"));
        var safety=Class.forName("com.shale.data.verification.CaseReadVerificationSafety");
        var parser=safety.getDeclaredMethod("schema",Path.class,String.class);parser.setAccessible(true);
        @SuppressWarnings("unchecked") var schema=(List<String>)parser.invoke(null,Path.of(Objects.requireNonNull(System.getenv("SHALE_CASE_VERIFY_SCHEMA"))),hash);
        var owner=ownerSource(System.getenv());
        var sequence=new ArrayList<String>();
        marker(Phase.OWNER_CONNECT,"BEGIN",0);
        long connecting=System.nanoTime();
        try(var c=owner.getConnection()) {
            requireOwnerNetworkTimeout(c);
            marker(Phase.OWNER_CONNECT,"PASS",(System.nanoTime()-connecting)/1_000_000);
            System.err.printf("AZURE_SETUP bounds loginSeconds=%d socketMillis=%d querySeconds=%d cancelSeconds=%d driver=%s%n",
                    owner.getLoginTimeout(),c.getNetworkTimeout(),owner.getQueryTimeout(),owner.getCancelQueryTimeout(),c.getMetaData().getDriverVersion());
            phase(c,Phase.OWNER_PREFLIGHT,()->{
            assertEquals(AiCaseDataAttestation.Platform.AZURE_SQL_DATABASE,AiCaseDataAttestation.platform(c));
            try(var s=c.createStatement();var r=s.executeQuery("SELECT DB_NAME(),USER_NAME(),(SELECT COUNT(*) FROM sys.objects WHERE is_ms_shipped=0),(SELECT COUNT(*) FROM sys.database_principals WHERE principal_id>4 AND type<>'R')")) {
                assertTrue(r.next());assertEquals(db,r.getString(1));assertEquals("dbo",r.getString(2));
                assertEquals(0,r.getInt(3),"Only an empty new disposable database may be provisioned");
                assertEquals(0,r.getInt(4),"Existing custom identities forbid fixture adoption");
            }
            });
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
            phase(c,Phase.V2_INSTALL,()->execute(c,Files.readString(Path.of("../docs/sql/ai-integration/phase3b8-confinement.sql"))));sequence.add("phase3b8-confinement.sql");
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
