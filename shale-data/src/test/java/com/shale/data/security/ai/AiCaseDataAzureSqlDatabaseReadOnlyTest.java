package com.shale.data.security.ai;

import org.junit.jupiter.api.*;
import javax.sql.DataSource;
import java.util.*;
import java.sql.*;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

/** Data-free native V2 gate. Setup is a separate opt-in/class; no installer or Case API call.
 * SELECT probes request zero rows; DML/DDL denial uses the existing live permission closure. */
class AiCaseDataAzureSqlDatabaseReadOnlyTest {
    static final List<String> IDENTITIES=List.of("AI","VERIFIER","AUTH","RUNTIME","BOOTSTRAP");
    static final Set<String> PUBLIC=Set.of("AiDataAuthenticateV1","AiDataSearchCasesV1","AiDataGetCaseOverviewV1","AiDataRecordCaseFailureV1","AiDataFinishCaseQuotaV1","AiDataAuthorizeCaseHandoffV1");
    static final List<String> PROTECTED=List.of("Cases","Users","auditLog","UserSessions","AiOAuthCredential","AiDelegatedSession","AiAuthorizationTransaction","AiConsentIntent","AiIntegrationClientKey");
    @Test @Timeout(60) void dedicatedContainedIdentitiesSatisfyLiveContract()throws Exception {
        Assumptions.assumeTrue("AZURE_SQL_DATABASE_READ_ONLY".equals(System.getenv("SHALE_AZURE_ATTESTATION_OPT_IN")),"Absent live Azure opt-in is NOT acceptance");
        validate(System.getenv()); // Every configuration check precedes the first connection.
        DataSource ai=source("AI"),verifier=source("VERIFIER"),auth=source("AUTH"),runtime=source("RUNTIME"),bootstrap=source("BOOTSTRAP");
        var evidence=new Properties();var sids=new HashSet<String>();
        for(String identity:IDENTITIES)try(var c=source(identity).getConnection()) {
            assertEquals(AiCaseDataAttestation.Platform.AZURE_SQL_DATABASE,AiCaseDataAttestation.platform(c));
            var token=new HashSet<String>();
            try(var s=statement(c);var r=s.executeQuery("SELECT name FROM sys.user_token")){while(r.next())token.add(r.getString(1));}
            var expected=new HashSet<>(Set.of(required(identity+"_USERNAME"),"public"));
            String role=switch(identity){case "AI"->"AiCaseDataRuntime";case "VERIFIER"->"AiCaseDataCatalogVerifier";case "BOOTSTRAP"->"AiIntegrationBootstrap";case "RUNTIME"->"AiIntegrationRuntime";default->null;};
            if(role!=null)expected.add(role);assertEquals(expected,token,"Exact acceptance token membership required");
            evidence.setProperty(identity+".tokens",String.join(",",new TreeSet<>(token)));
            try(var s=statement(c);var r=s.executeQuery("""
                    SELECT p.sid,p.type,p.authentication_type,p.principal_id,USER_NAME(),ORIGINAL_LOGIN(),
                      DB_NAME(),ses.authenticating_database_id,DB_ID(),ses.original_security_id,
                      CONVERT(nvarchar(128),SERVERPROPERTY('ServerName'))
                    FROM sys.database_principals p JOIN sys.dm_exec_sessions ses ON ses.session_id=@@SPID
                    WHERE p.principal_id=USER_ID()
                    """)) {
                assertTrue(r.next(),identity+" own-session evidence required");byte[] sid=r.getBytes(1);
                assertNotNull(sid);assertTrue(sid.length>0);
                assertTrue(sids.add(HexFormat.of().formatHex(sid)),"Every acceptance identity requires an independent SID");
                assertEquals("S",r.getString(2));assertEquals(2,r.getInt(3));assertTrue(r.getInt(4)>4);
                assertEquals(required(identity+"_USERNAME"),r.getString(5));assertEquals(r.getString(5),r.getString(6));
                assertEquals(required("DATABASE"),r.getString(7));assertEquals(r.getInt(9),r.getInt(8));
                assertTrue(Arrays.equals(sid,r.getBytes(10)),"Original SID must equal contained SID");
                String server=r.getString(11);assertNotNull(server);
                if(evidence.containsKey("logicalServer"))assertEquals(evidence.getProperty("logicalServer"),server);
                evidence.setProperty("logicalServer",server);assertFalse(r.next());
            }
            evidence.setProperty(identity+".containedIndependentIdentity","PASS");
        }
        var forbidden=List.of(auth,runtime,bootstrap);long started=System.nanoTime();
        attestation(ai,verifier,forbidden).verify();
        long elapsed=(System.nanoTime()-started)/1_000_000;
        assertTrue(elapsed<10_000,"Native attestation must fit the unchanged ten-second request budget");
        evidence.setProperty("attestationMillis",Long.toString(elapsed));
        // Configuration substitution only, with valid credentials; no role/catalog mutation.
        assertThrows(IllegalStateException.class,()->attestation(ai,ai,forbidden).verify());
        assertThrows(IllegalStateException.class,()->attestation(ai,verifier,List.of(ai,runtime,bootstrap)).verify());
        assertThrows(IllegalStateException.class,()->attestation(ai,verifier,List.of(auth,runtime,verifier)).verify());
        assertThrows(IllegalStateException.class,()->attestation(ai,verifier,List.of(auth,runtime,runtime)).verify());
        int denials=0;
        for(String identity:List.of("AI","VERIFIER","BOOTSTRAP"))try(var c=source(identity).getConnection()) {
            for(String table:PROTECTED){denied(c,"SELECT TOP (0) * FROM dbo.["+table+"]",Set.of(229));denials++;}
            boolean impersonated=false;
            try {try(var s=statement(c)){s.execute("EXECUTE AS USER='dbo'");impersonated=true;}}
            catch(SQLException refusal){assertTrue(Set.of(15517,15151,229).contains(refusal.getErrorCode()),"Expected impersonation refusal, not connectivity/syntax failure");denials++;}
            finally{if(impersonated)try(var s=statement(c)){s.execute("REVERT");}}
            assertFalse(impersonated,"Dedicated identity must not impersonate dbo");
            denied(c,"SELECT TOP (0) * FROM master.sys.objects",Set.of(40515,916,229));denials++;
        }
        try(var c=verifier.getConnection()) {
            AiCaseDataLocalSqlServerTest.sql(c,Files.readString(Path.of("../docs/sql/ai-integration/verify.sql")));
            var objects=new ArrayList<String>();
            try(var s=statement(c);var r=s.executeQuery("SELECT QUOTENAME(s.name)+'.'+QUOTENAME(o.name) FROM sys.objects o JOIN sys.schemas s ON s.schema_id=o.schema_id WHERE o.is_ms_shipped=0 AND o.type='P'")){while(r.next())objects.add(r.getString(1));}
            for(String identity:List.of("AI","VERIFIER","BOOTSTRAP"))try(var caller=source(identity).getConnection()) {
                var executable=new HashSet<String>();
                for(String object:objects)try(var p=caller.prepareStatement("SELECT HAS_PERMS_BY_NAME(?,'OBJECT','EXECUTE')")) {
                    p.setQueryTimeout(3);p.setString(1,object);try(var r=p.executeQuery()){assertTrue(r.next());if(r.getInt(1)==1)executable.add(object);}
                }
                if(identity.equals("AI"))assertEquals(PUBLIC.stream().map(n->"[dbo].["+n+"]").collect(java.util.stream.Collectors.toSet()),executable);
                else if(identity.equals("VERIFIER"))assertTrue(executable.isEmpty(),"Verifier may execute no application procedure");
                else for(String name:PUBLIC)assertFalse(executable.contains("[dbo].["+name+"]"),"Bootstrap must not inherit confined runtime authority");
            }
            try(var s=statement(c);var r=s.executeQuery("SELECT CONVERT(nvarchar(128),SERVERPROPERTY('ProductVersion')),CONVERT(nvarchar(128),SERVERPROPERTY('Edition'))")) {
                assertTrue(r.next());evidence.setProperty("productVersion",r.getString(1));evidence.setProperty("edition",r.getString(2));
            }
        }
        evidence.setProperty("gate","READ-ONLY NATIVE AZURE GATE");evidence.setProperty("contract","V2");evidence.setProperty("engineEdition","5");
        evidence.setProperty("deniedStatementChecks",Integer.toString(denials));evidence.setProperty("identitySubstitutionRejections","4");
        evidence.setProperty("caseCatalog","6e9182b6e9b75200a16d51dbd537ca8926b358d603220d287a9710ad6ca8063a");
        evidence.setProperty("sourceReceipt","60f7b39727a95ca65afd26ba3f8206a2700b64ca4df512da89df070a9f92e891");
        evidence.setProperty("runtimeTokens","own,public,AiCaseDataRuntime");evidence.setProperty("verifierTokens","own,public,AiCaseDataCatalogVerifier");
        evidence.setProperty("catalogAndEffectiveAuthority","PASS");evidence.setProperty("nativeMutationFaultGate","OPEN");
        evidence.setProperty("result","READ_ONLY_SUBGATE_PASS_PENDING_OPERATOR_PACKET");
        Path output=Path.of("../work/azure-native/read-only.properties");Files.createDirectories(output.getParent());
        try(var writer=Files.newBufferedWriter(output)){evidence.store(writer,"No SIDs, credentials, row data or module text");}
    }
    private static AiCaseDataAttestation attestation(DataSource ai,DataSource verifier,List<DataSource> forbidden) {
        return new AiCaseDataAttestation(ai,verifier,forbidden,required("DATABASE"),required("ISSUER"),required("RESOURCE"),true,AiCaseDataAttestation.Contract.V2);
    }
    static void validate(Map<String,String> env) {
        String db=setting(env,"DATABASE"),url=setting(env,"JDBC_URL");
        require(db.matches("ShaleAzureReadOnly_[a-f0-9]{32}"),"Fresh disposable database name required");
        require(url.matches("jdbc:sqlserver://[a-z0-9-]+\\.database\\.windows\\.net(?::1433)?;databaseName="+db+";?"),"Explicit Azure target and validated TLS defaults required");
        require(!url.startsWith("jdbc:sqlserver://shale.database.windows.net"),"Production server forbidden");
        require(setting(env,"CONTRACT_VERSION").equals("V2"),"V2 required");
        require(setting(env,"ISSUER").equals(AiCaseDataLocalSqlServerTest.ISSUER)&&setting(env,"RESOURCE").equals(AiCaseDataLocalSqlServerTest.RESOURCE),"Reserved synthetic deployment binding required");
        var users=new HashSet<String>();var passwords=new HashSet<String>();
        for(String identity:IDENTITIES) {
            String user=setting(env,identity+"_USERNAME");require(user.equals("azro_"+identity.toLowerCase(Locale.ROOT)),"Reviewed contained name required");
            require(users.add(user.toLowerCase(Locale.ROOT)),"Distinct configured principals required");
            require(passwords.add(setting(env,identity+"_PASSWORD")),"Independent secrets required");
        }
    }
    static DataSource source(String identity) {
        return source(System.getenv(),identity);
    }
    static com.microsoft.sqlserver.jdbc.SQLServerDataSource source(Map<String,String> env,String identity) {
        String url=setting(env,"JDBC_URL");
        // Explicit database; no auth/TLS overrides or alternate transport.
        assertTrue(url.matches("jdbc:sqlserver://[A-Za-z0-9.-]+(?::[0-9]+)?;databaseName=[A-Za-z0-9_-]+;?"),"Dedicated SQL-auth URL with explicit target database required");
        var source=new com.microsoft.sqlserver.jdbc.SQLServerDataSource();
        source.setURL(url);source.setUser(setting(env,identity+"_USERNAME"));source.setPassword(setting(env,identity+"_PASSWORD"));
        source.setEncrypt("true");source.setTrustServerCertificate(false);source.setLoginTimeout(3);source.setSocketTimeout(5000);
        return source;
    }
    static String required(String suffix){return setting(System.getenv(),suffix);}
    static String setting(Map<String,String> env,String suffix) {
        String value=env.get("SHALE_AZURE_"+suffix);
        if(value==null||value.isBlank())throw new IllegalStateException("Missing read-only Azure acceptance setting: "+suffix);
        return value;
    }
    private static void require(boolean condition,String message){if(!condition)throw new IllegalArgumentException(message);}
    private static Statement statement(Connection c)throws SQLException{var s=c.createStatement();s.setQueryTimeout(3);return s;}
    private static void denied(Connection c,String text,Set<Integer> codes) {
        SQLException refusal=assertThrows(SQLException.class,()->{try(var s=statement(c)){s.execute(text);}},"Statement must be denied");
        assertTrue(codes.contains(refusal.getErrorCode()),"Expected authority refusal, not connectivity/syntax failure; SQL code="+refusal.getErrorCode());
    }
}
