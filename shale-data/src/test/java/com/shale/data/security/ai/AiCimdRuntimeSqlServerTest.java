package com.shale.data.security.ai;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/** Real restricted TLS logins on a deliberately incomplete disposable schema.
 * Tests identity authority and refusal only; never substitutes for authoritative V1/V2 acceptance. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AiCimdRuntimeSqlServerTest {
    String prefix,db,port,password;
    final Map<String,String> secrets=new LinkedHashMap<>();
    @BeforeAll void identities()throws Exception {
        Assumptions.assumeTrue("LOCAL_DISPOSABLE_CIMD".equals(System.getenv("SHALE_CIMD_SQL_OPT_IN")));
        port=System.getenv("SHALE_CIMD_SQL_PORT"); password=System.getenv("SHALE_CIMD_SQL_PASSWORD");
        assertNotNull(port); assertTrue(port.matches("[0-9]{4,5}")); assertNotNull(password);
        prefix="Cimd_"+UUID.randomUUID().toString().replace("-","");
        try(var c=owner("master")) {
            assertTrue(Set.of(2,3,4).contains(scalar(c,"SELECT CONVERT(int,SERVERPROPERTY('EngineEdition'))")),"Local disposable SQL only");
            var keys=new ArrayList<String>(); for(var role:AiCimdIdentity.values()) keys.add(role.key());
            keys.addAll(List.of("auth","runtime","bootstrap","legacy-case"));
            for(String key:keys) {
                String secret=UUID.randomUUID()+"aB9!"; secrets.put(key,secret);
                execute(c,"CREATE LOGIN "+quote(prefix+"_"+key)+" WITH PASSWORD='"+secret+"',CHECK_POLICY=OFF");
            }
            execute(c,"GRANT VIEW ANY DEFINITION TO "+quote(prefix+"_catalog"));
        }
    }
    @BeforeEach void database()throws Exception {
        db=prefix+"_db_"+UUID.randomUUID().toString().replace("-","");
        try(var c=owner("master")) { execute(c,"CREATE DATABASE "+quote(db)); }
        try(var c=owner(db)) {
            for(String key:secrets.keySet()) execute(c,"CREATE USER "+quote(prefix+"_"+key)+" FOR LOGIN "+quote(prefix+"_"+key));
            execute(c,"CREATE TABLE dbo.SyntheticSecret(Id int, Value nvarchar(50)); INSERT dbo.SyntheticSecret VALUES(1,N'private')");
            for(var role:AiCimdIdentity.values()) {
                execute(c,"CREATE ROLE "+role.role()+" AUTHORIZATION dbo; ALTER ROLE "+role.role()+" ADD MEMBER "+quote(prefix+"_"+role.key()));
                if(role!=AiCimdIdentity.CATALOG) execute(c,"DENY SELECT ON dbo.SyntheticSecret TO "+role.role());
                for(String root:role.roots()) {
                    execute(c,"CREATE PROCEDURE dbo."+root+" AS SELECT 1 AS SyntheticResult");
                    execute(c,"GRANT EXECUTE ON dbo."+root+" TO "+role.role());
                }
            }
            execute(c,"GRANT VIEW DEFINITION TO AiCimdCatalogV1");
        }
    }
    @AfterEach void removeDatabase()throws Exception { if(db!=null) try(var c=owner("master")) { execute(c,"ALTER DATABASE "+quote(db)+" SET SINGLE_USER WITH ROLLBACK IMMEDIATE; DROP DATABASE "+quote(db)); db=null; } }
    @AfterAll void removeLogins()throws Exception { if(prefix!=null) try(var c=owner("master")) { for(String key:secrets.keySet()) execute(c,"DROP LOGIN "+quote(prefix+"_"+key)); } }
    @Test void actualSeparateRestrictedSessionsHaveExactlyTheirDeclaredRootsAndNoTableAuthority()throws Exception {
        for(var role:AiCimdIdentity.values()) assertDoesNotThrow(()->check(role),role.name());
        try(var c=source("browser").getConnection()) {
            assertThrows(SQLException.class,()->execute(c,"SELECT * FROM dbo.SyntheticSecret"));
            assertThrows(SQLException.class,()->execute(c,"EXEC dbo.AiCimdRedeemV1"));
            assertEquals(1,scalar(c,"SELECT CASE WHEN SUSER_SID()=(SELECT sid FROM sys.database_principals WHERE principal_id=USER_ID()) THEN 1 ELSE 0 END"));
        }
    }
    @Test void columnExceptionInheritedRolesAndScopedImpersonationRemoveReadiness()throws Exception {
        for(String[] change:new String[][] {
                {"GRANT SELECT ON dbo.SyntheticSecret(Value) TO AiCimdOAuthBrowserV1","REVOKE SELECT ON dbo.SyntheticSecret(Value) FROM AiCimdOAuthBrowserV1"},
                {"ALTER ROLE db_datareader ADD MEMBER "+quote(prefix+"_browser"),"ALTER ROLE db_datareader DROP MEMBER "+quote(prefix+"_browser")},
                {"GRANT IMPERSONATE ON USER::"+quote(prefix+"_issuer")+" TO AiCimdOAuthBrowserV1","REVOKE IMPERSONATE ON USER::"+quote(prefix+"_issuer")+" FROM AiCimdOAuthBrowserV1"}}) {
            try(var c=owner(db)) { execute(c,change[0]); }
            try {
                if(change[0].startsWith("GRANT SELECT ON dbo.SyntheticSecret(Value)")) try(var caller=source("browser").getConnection();var st=caller.createStatement();var rows=st.executeQuery("SELECT Value FROM dbo.SyntheticSecret")) {
                    assertTrue(rows.next(),"Real column GRANT overrides table DENY and must remove readiness.");
                }
                assertThrows(Exception.class,()->check(AiCimdIdentity.BROWSER));
            }
            finally { try(var c=owner(db)) { execute(c,change[1]); } }
            check(AiCimdIdentity.BROWSER);
        }
    }
    @Test void ownershipPublicChainsAndOtherDatabaseMappingsAreRejected()throws Exception {
        try(var c=owner(db)) { execute(c,"CREATE SCHEMA Pr4cOwnedSchema AUTHORIZATION "+quote(prefix+"_browser")); }
        assertThrows(Exception.class,()->check(AiCimdIdentity.BROWSER));
        try(var c=owner(db)) { execute(c,"DROP SCHEMA Pr4cOwnedSchema"); execute(c,"CREATE VIEW dbo.Pr4cBridge AS SELECT Value FROM dbo.SyntheticSecret"); execute(c,"GRANT SELECT ON dbo.Pr4cBridge TO public"); }
        assertThrows(Exception.class,()->check(AiCimdIdentity.BROWSER));
        try(var c=owner(db)) { execute(c,"DROP VIEW dbo.Pr4cBridge"); }
        String other=prefix+"_other";
        try(var c=owner("master")) { execute(c,"CREATE DATABASE "+quote(other)); }
        try {
            try(var c=owner(other)) { execute(c,"CREATE USER "+quote(prefix+"_browser")+" FOR LOGIN "+quote(prefix+"_browser")); }
            assertThrows(Exception.class,()->check(AiCimdIdentity.BROWSER));
        } finally { try(var c=owner("master")) { execute(c,"ALTER DATABASE "+quote(other)+" SET SINGLE_USER WITH ROLLBACK IMMEDIATE; DROP DATABASE "+quote(other)); } }
        check(AiCimdIdentity.BROWSER);
    }
    @Test void serverScopedGrantsAndCatalogColumnReadsAreRejected()throws Exception {
        String login=quote(prefix+"_browser"),other=quote(prefix+"_issuer");
        try(var c=owner("master")) { execute(c,"GRANT IMPERSONATE ON LOGIN::"+other+" TO "+login); }
        try { assertThrows(Exception.class,()->check(AiCimdIdentity.BROWSER)); }
        finally { try(var c=owner("master")) { execute(c,"REVOKE IMPERSONATE ON LOGIN::"+other+" FROM "+login); } }
        try(var c=owner(db)) { execute(c,"GRANT SELECT(Value) ON dbo.SyntheticSecret TO "+quote(prefix+"_catalog")); }
        assertThrows(Exception.class,()->check(AiCimdIdentity.CATALOG));
    }
    @Test void fixedCatalogModuleCompilesButMissingCompatibilityReceiptRefuses()throws Exception {
        String body,expected;
        try(var stream=getClass().getResourceAsStream("/ai/cimd-runtime-v1-catalog.sql");var hash=getClass().getResourceAsStream("/ai/cimd-runtime-v1-catalog.sha256")) {
            assertNotNull(stream); assertNotNull(hash);
            body=new String(stream.readAllBytes(),StandardCharsets.UTF_8); expected=new String(hash.readAllBytes(),StandardCharsets.US_ASCII).strip();
        }
        try(var c=owner(db)) {
            execute(c,"DROP PROCEDURE dbo.AiCimdAttestCatalogV1"); execute(c,body);
            execute(c,"GRANT EXECUTE ON dbo.AiCimdAttestCatalogV1 TO AiCimdCatalogV1");
        }
        try(var c=source("catalog").getConnection();var st=c.createStatement();var rows=st.executeQuery("SELECT definition FROM sys.sql_modules WHERE object_id=OBJECT_ID('dbo.AiCimdAttestCatalogV1')")) {
            assertTrue(rows.next()); assertEquals(expected,HexFormat.of().formatHex(AiSql.hash(rows.getString(1).getBytes(StandardCharsets.UTF_16LE))));
        }
        try(var c=source("catalog").getConnection()) {
            assertEquals(59600,assertThrows(SQLException.class,()->execute(c,"EXEC dbo.AiCimdAttestCatalogV1")).getErrorCode(),"A missing compatibility receipt cannot be accepted by the owner-context gate");
        }
        try(var c=source("browser").getConnection()) {
            assertEquals(229,assertThrows(SQLException.class,()->execute(c,"EXEC dbo.AiCimdAttestCatalogV1")).getErrorCode(),"Business identities cannot enter the catalog execution boundary");
        }
    }
    @Test void ownerCatalogContextDoesNotInventServerVisibility()throws Exception {
        try(var c=owner(db)) {
            execute(c,"CREATE PROCEDURE dbo.OwnerMetadataProbe WITH EXECUTE AS OWNER AS SELECT HAS_PERMS_BY_NAME(NULL,NULL,'VIEW ANY DEFINITION') AS ServerVisibility, (SELECT COUNT(*) FROM sys.server_principals WHERE name LIKE '"+prefix+"%') AS VisibleLogins");
            execute(c,"GRANT EXECUTE ON dbo.OwnerMetadataProbe TO AiCimdCatalogV1");
        }
        try(var c=source("catalog").getConnection();var st=c.createStatement();var r=st.executeQuery("EXEC dbo.OwnerMetadataProbe")) {
            assertTrue(r.next());
            assertEquals(0,r.getInt(1),"Owner execution must not invent server metadata authority; the unchanged predecessor verifier remains a blocker.");
            assertEquals(0,r.getInt(2),"A filtered server-principal inventory cannot prove identity absence.");
        }
    }
    @Test void incompleteSchemaAndSubstitutedIdentityCannotActivateRuntime()throws Exception {
        var sources=new EnumMap<AiCimdIdentity,DataSource>(AiCimdIdentity.class);
        var users=new EnumMap<AiCimdIdentity,String>(AiCimdIdentity.class);
        var endpoints=new EnumMap<AiCimdIdentity,AiCimdRuntime.Endpoint>(AiCimdIdentity.class);
        for(var role:AiCimdIdentity.values()) {
            sources.put(role,source(role.key())); users.put(role,prefix+"_"+role.key());
            endpoints.put(role,new AiCimdRuntime.Endpoint("jdbc:sqlserver://localhost:"+port+";databaseName="+db,users.get(role),secrets.get(role.key())));
        }
        var forbidden=List.of(source("auth"),source("runtime"),source("bootstrap"),source("legacy-case"));
        var attestor=new AiCimdRuntimeAttestation(sources,users,forbidden,db,"https://issuer.test/oauth","https://issuer.test/mcp",AiCaseDataAttestation.Contract.V2);
        assertThrows(IllegalStateException.class,attestor::verify,"A plausible restricted schema without historical receipts cannot be ready");
        var config=new AiCimdRuntime.Configuration(endpoints,db,"https://issuer.test/oauth","https://issuer.test/mcp",AiCaseDataAttestation.Contract.V2);
        assertThrows(IllegalStateException.class,()->AiCimdRuntime.open(config,forbidden));
        try(var c=owner(db)) { assertEquals(0,scalar(c,"SELECT COUNT(*) FROM sys.dm_exec_sessions WHERE database_id=DB_ID() AND program_name LIKE 'ShaleCimd-%'"),"Failed startup must close every dedicated pool"); }
        sources.put(AiCimdIdentity.ISSUER,source("browser"));
        assertThrows(IllegalStateException.class,()->new AiCimdRuntimeAttestation(sources,users,forbidden,db,"https://issuer.test/oauth","https://issuer.test/mcp",AiCaseDataAttestation.Contract.V2).verify());
    }
    private void check(AiCimdIdentity role)throws Exception {
        try(var caller=source(role.key()).getConnection();var review=source("catalog").getConnection()) {
            AiCimdRuntimeAttestation.verifyIdentity(caller,review,AiCimdRuntimeAttestation.identity(caller),role,AiCaseDataAttestation.Platform.SQL_SERVER_OR_MI);
        }
    }
    DataSource source(String key) { var s=new com.microsoft.sqlserver.jdbc.SQLServerDataSource(); s.setURL(url(db)); s.setUser(prefix+"_"+key); s.setPassword(secrets.get(key)); return s; }
    Connection owner(String database)throws SQLException { return DriverManager.getConnection(url(database),"sa",password); }
    String url(String database) { return "jdbc:sqlserver://localhost:"+port+";databaseName="+database+";encrypt=true;trustServerCertificate=false;loginTimeout=5;socketTimeout=30000"; }
    static int scalar(Connection c,String sql)throws SQLException { try(var s=c.createStatement();var r=s.executeQuery(sql)) { assertTrue(r.next()); return r.getInt(1); } }
    static void execute(Connection c,String sql)throws SQLException { try(var s=c.createStatement()) { s.setQueryTimeout(20); boolean result=s.execute(sql); while(true) { if(result) try(var r=s.getResultSet()) { while(r.next()) { } } else if(s.getUpdateCount()==-1) break; result=s.getMoreResults(Statement.CLOSE_CURRENT_RESULT); } } }
    static String quote(String s) { return "["+s.replace("]","]]")+"]"; }
}
