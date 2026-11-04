package com.shale.data.security.ai;

import org.junit.jupiter.api.*;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Tiny loopback-only authority/driver fixture; no private schema, migrations or Azure. */
class AzureCatalogAuthorityLocalSqlServerTest {
    private final AiCaseDataLocalSqlServerTest local=new AiCaseDataLocalSqlServerTest();
    private String db;
    @BeforeEach void create()throws Exception {
        Assumptions.assumeTrue("LOCAL_CATALOG_AUTHORITY".equals(System.getenv("SHALE_CATALOG_SQL_OPT_IN")));
        local.port=System.getenv("SHALE_CATALOG_SQL_PORT");local.password=System.getenv("SHALE_CATALOG_SQL_PASSWORD");
        assertNotNull(local.port);assertTrue(local.port.matches("[0-9]{4,5}"));assertNotNull(local.password);
        try(var c=local.owner("master")) {
            assertTrue(Set.of(2,3,4).contains(AiCaseDataLocalSqlServerTest.count(c,"SELECT CONVERT(int,SERVERPROPERTY('EngineEdition'))")),"Boxed local SQL only");
            db="CatalogProbe_"+UUID.randomUUID().toString().replace("-","");sql(c,"CREATE DATABASE ["+db+"]");
        }
        try(var c=local.owner(db)) {
            sql(c,"CREATE ROLE AiCaseDataRuntime AUTHORIZATION dbo; CREATE ROLE AiCaseDataCatalogVerifier AUTHORIZATION dbo; CREATE USER catalog_probe WITHOUT LOGIN; ALTER ROLE AiCaseDataCatalogVerifier ADD MEMBER catalog_probe; CREATE ROLE AiIntegrationRuntime; CREATE ROLE AiIntegrationBootstrap; CREATE ROLE AiCapabilityOperator; CREATE TABLE dbo.AiCaseDataDeployment(Id int PRIMARY KEY); CREATE TABLE dbo.AiCaseDataInvocation(Id int PRIMARY KEY,TenantId int NOT NULL DEFAULT 7,Computed AS Id+1,CHECK(Id>0)); CREATE INDEX IX_probe ON dbo.AiCaseDataInvocation(TenantId);");
            sql(c,"CREATE SCHEMA sec AUTHORIZATION dbo;");
            sql(c,"CREATE FUNCTION sec.fn_FilterByTenant(@TenantId int) RETURNS TABLE WITH SCHEMABINDING AS RETURN SELECT 1 AS allowed WHERE @TenantId=7;");
            sql(c,"CREATE SECURITY POLICY sec.probe_policy ADD FILTER PREDICATE sec.fn_FilterByTenant(TenantId) ON dbo.AiCaseDataInvocation WITH(STATE=ON);");
            sql(c,"CREATE VIEW dbo.AiDataNormalizeV1 AS SELECT Id FROM dbo.AiCaseDataInvocation;");
            sql(c,"ALTER TABLE dbo.AiCaseDataInvocation ADD CONSTRAINT FK_probe FOREIGN KEY(Id) REFERENCES dbo.AiCaseDataDeployment(Id);");
            sql(c,"CREATE TRIGGER dbo.AiDataImmutableInvocation ON dbo.AiCaseDataInvocation AFTER INSERT AS RETURN;");
            String installer=Files.readString(Path.of("../docs/sql/ai-integration/phase3b8-confinement.sql"));
            for(String line:installer.lines().toList())if(line.startsWith("CREATE TABLE dbo.AiDataUnicode")||line.startsWith("CREATE TABLE dbo.AiDataWordTransition"))sql(c,line);
            sql(c,"INSERT dbo.AiCaseDataDeployment VALUES(1); INSERT dbo.AiCaseDataInvocation(Id,TenantId) VALUES(1,7); INSERT dbo.AiDataUnicodeRange VALUES(65,65,1,0,1,1); INSERT dbo.AiDataUnicodeLower VALUES(65,N'a'); INSERT dbo.AiDataWordTransition VALUES(0,1,0,0,0);");
            var grants=installer.lines().filter(l->l.startsWith("GRANT ")&&l.endsWith(" TO AiCaseDataCatalogVerifier;")).toList();
            assertEquals(6,grants.size(),"Use exactly the installer's existing database/SELECT grants");
            for(String grant:grants)sql(c,grant);
        }
    }
    @AfterEach void remove()throws Exception {
        if(db!=null)try(var c=local.owner("master")){sql(c,"ALTER DATABASE ["+db+"] SET SINGLE_USER WITH ROLLBACK IMMEDIATE; DROP DATABASE ["+db+"]");}
    }
    private Connection restricted()throws Exception {
        var c=local.owner(db);c.setNetworkTimeout(null,30_000);sql(c,"EXECUTE AS USER='catalog_probe';");
        assertEquals(0,AiCaseDataLocalSqlServerTest.count(c,"SELECT IS_MEMBER('db_owner')"));
        assertEquals(0,AiCaseDataLocalSqlServerTest.count(c,"SELECT HAS_PERMS_BY_NAME(DB_NAME(),'DATABASE','CONTROL')"));
        assertEquals(1,AiCaseDataLocalSqlServerTest.count(c,"SELECT HAS_PERMS_BY_NAME(DB_NAME(),'DATABASE','VIEW SECURITY DEFINITION')"));
        return c;
    }
    @Test void exactNineteenComponentBatchWorksWithOnlyExistingVerifierAuthority()throws Exception {
        try(var owner=local.owner(db);var verifier=restricted()) {
            owner.setNetworkTimeout(null,30_000);
            var expected=AzureCatalogComponents.read(owner);
            var actual=AzureCatalogComponents.read(verifier);
            assertEquals(19,actual.size());assertEquals(expected,actual,"Database VIEW DEFINITION plus the existing SELECT grants must suffice");
            assertTrue(actual.get(5).bytes()>0,"Security predicate must be visible");
            assertTrue(actual.get(12).bytes()>0,"Dependency must be visible");
            assertThrows(SQLException.class,()->sql(verifier,"SELECT * FROM dbo.AiCaseDataInvocation"),"Metadata authority must not imply application SELECT");
        }
    }
    @Test void laterCompileErrorCanRejectBatchBeforeFirstResult()throws Exception {
        try(var c=restricted();var s=c.createStatement()) {
            s.setQueryTimeout(20);
            var failure=assertThrows(SQLException.class,()->s.execute("SELECT 1 AS FirstResult; SELECT nonexistent_probe_column FROM sys.objects;"));
            assertEquals(207,failure.getErrorCode());
        }
    }
    @Test void permissionFailureTimingMustNotBeInferredFromStatementPosition()throws Exception {
        try(var c=restricted();var s=c.createStatement()) {
            s.setQueryTimeout(20);
            boolean first;
            try { first=s.execute("SELECT 1 AS FirstResult; SELECT * FROM dbo.AiCaseDataInvocation;"); }
            catch(SQLException failure){assertEquals(229,failure.getErrorCode());System.out.println("LOCAL_CATALOG laterPermissionFailure=EXECUTE");return;}
            assertTrue(first);
            var failure=assertThrows(SQLException.class,()->{while(s.getMoreResults(Statement.CLOSE_CURRENT_RESULT))try(var r=s.getResultSet()){while(r.next()){} }});
            assertEquals(229,failure.getErrorCode());
            System.out.println("LOCAL_CATALOG laterPermissionFailure=TRAVERSAL");
        }
    }
    @Test void queryTimeoutCanFailExecuteBeforeAnyResultWithoutChangingOrdinaryLimit()throws Exception {
        try(var c=restricted();var s=c.createStatement()) {
            s.setQueryTimeout(20);
            var failure=assertThrows(SQLException.class,()->s.execute("WAITFOR DELAY '00:00:25'; SELECT 1;"));
            assertEquals("HY008",failure.getSQLState(),"Pinned driver cancellation, not missing authority");
        }
    }
    @Test void existingFiveSecondSocketBoundCanPreemptTwentySecondQueryLimit()throws Exception {
        try(var c=DriverManager.getConnection(local.url(db).replace("socketTimeout=10000","socketTimeout=5000"),"sa",local.password)) {
            c.setNetworkTimeout(null,5000);
            sql(c,"EXECUTE AS USER='catalog_probe';");
            try(var s=c.createStatement()) {
                s.setQueryTimeout(20);
                var failure=assertThrows(SQLException.class,()->s.execute("WAITFOR DELAY '00:00:08'; SELECT 1;"));
                assertEquals("08S01",failure.getSQLState(),"Socket failure can occur inside execute before query cancellation");
            }
        }
    }
    private static void sql(Connection c,String text)throws SQLException{AiCaseDataLocalSqlServerTest.sql(c,text);}
}
