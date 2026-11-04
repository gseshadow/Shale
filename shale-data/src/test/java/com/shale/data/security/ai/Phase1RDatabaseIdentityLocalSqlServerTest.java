package com.shale.data.security.ai;

import org.junit.jupiter.api.*;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Real SQL batch/receipt regressions on a new loopback-only synthetic fixture.
 * Edition injection exercises dispatch, not native Azure metadata/lifecycle acceptance. */
class Phase1RDatabaseIdentityLocalSqlServerTest {
    private final AiCaseDataLocalSqlServerTest local=new AiCaseDataLocalSqlServerTest();
    private String db,engine;
    private static final String PROPERTY="Shale.Phase1R.v1";
    @BeforeEach void create()throws Exception {
        Assumptions.assumeTrue("LOCAL_DISPOSABLE_PHASE1R_IDENTITY".equals(System.getenv("SHALE_PHASE1R_IDENTITY_OPT_IN")),
                "Dedicated disposable loopback SQL opt-in required");
        local.port=System.getenv("SHALE_PHASE1R_IDENTITY_PORT");
        local.password=System.getenv("SHALE_PHASE1R_IDENTITY_PASSWORD");
        assertNotNull(local.port);assertTrue(local.port.matches("[0-9]{4,5}"));
        assertTrue(Integer.parseInt(local.port)>=1024&&Integer.parseInt(local.port)<=65535);assertNotNull(local.password);
        try(var c=local.owner("master")) {
            assertTrue(Set.of(2,3,4).contains(AiCaseDataLocalSqlServerTest.count(c,"SELECT CONVERT(int,SERVERPROPERTY('EngineEdition'))")),
                    "Only the dedicated boxed loopback test instance is allowed");
            String owned="Phase1RIdentity_"+UUID.randomUUID().toString().replace("-","");
            sql(c,"CREATE DATABASE ["+owned+"] COLLATE SQL_Latin1_General_CP1_CI_AS");db=owned;
        }
        try(var c=local.owner(db)) {
            sql(c,"CREATE SCHEMA sec AUTHORIZATION dbo");
            sql(c,"CREATE TABLE dbo.Users(ShaleClientId int); CREATE TABLE dbo.auditLog(ShaleClientId int); CREATE TABLE dbo.Statuses(ShaleClientId int); CREATE TABLE dbo.Other(ShaleClientId int)");
            sql(c,"CREATE FUNCTION sec.fn_FilterByTenant(@id int) RETURNS TABLE WITH SCHEMABINDING AS RETURN SELECT 1 AS allowed WHERE @id=CONVERT(int,SESSION_CONTEXT(N'ShaleClientId'))");
            sql(c,"CREATE FUNCTION sec.fn_FilterByTenantOrGlobal(@id int) RETURNS TABLE WITH SCHEMABINDING AS RETURN SELECT 1 AS allowed WHERE @id IS NULL OR @id=CONVERT(int,SESSION_CONTEXT(N'ShaleClientId'))");
            sql(c,"CREATE SECURITY POLICY sec.TenantFilter ADD FILTER PREDICATE sec.fn_FilterByTenant(ShaleClientId) ON dbo.Other WITH(STATE=ON,SCHEMABINDING=ON)");
            engine=Files.readString(Path.of("../docs/sql/phase1r/engine.sql"))
                    .replace("$(ExpectedDatabase)",db).replace("$(ReviewedCollation)","SQL_Latin1_General_CP1_CI_AS")
                    .replace("$(ReviewedStrictHash)",value(c,"SELECT CONVERT(varchar(64),HASHBYTES('SHA2_256',OBJECT_DEFINITION(OBJECT_ID('sec.fn_FilterByTenant'))),2)"))
                    .replace("$(ReviewedOverlayHash)",value(c,"SELECT CONVERT(varchar(64),HASHBYTES('SHA2_256',OBJECT_DEFINITION(OBJECT_ID('sec.fn_FilterByTenantOrGlobal'))),2)"))
                    .replace("$(ReviewedOtherPredicatesHash)",value(c,"SELECT CONVERT(varchar(64),HASHBYTES('SHA2_256',(SELECT p.* FROM sys.security_predicates p ORDER BY p.object_id,p.security_predicate_id FOR JSON PATH)),2)"));
        }
    }
    @AfterEach void remove()throws Exception {
        if(db!=null)try(var c=local.owner("master")){sql(c,"ALTER DATABASE ["+db+"] SET SINGLE_USER WITH ROLLBACK IMMEDIATE; DROP DATABASE ["+db+"]");}
    }
    @Test void boxedReceiptRetainsHistoricalVersionGuidAndExactReplay()throws Exception {
        try(var c=local.owner(db)) {
            // Unsupported Azure column must remain unbound on SQL Server/MI.
            String boxed=engine.replace("physical_database_name","Phase1RUnavailableAzureColumn");
            for(String mode:List.of("PREFLIGHT","APPLY","POSTCHECK"))run(c,boxed,mode);
            assertEquals("1",value(c,"SELECT JSON_VALUE(CONVERT(nvarchar(4000),value),'$.version') FROM sys.extended_properties WHERE name='"+PROPERTY+"'"));
            assertEquals(value(c,"SELECT CONVERT(varchar(36),database_guid) FROM sys.database_recovery_status WHERE database_id=DB_ID()"),
                    value(c,"SELECT JSON_VALUE(CONVERT(nvarchar(4000),value),'$.database') FROM sys.extended_properties WHERE name='"+PROPERTY+"'"));
            String receipt=receipt(c);run(c,boxed,"APPLY");assertEquals(receipt,receipt(c));
            assertEquals("10",value(c,"SELECT COUNT(*) FROM OPENJSON(N'"+receipt.replace("'","''")+"')"));
            run(c,boxed,"ROLLBACK");assertNull(receipt(c));assertEquals("1",predicates(c));run(c,boxed,"ROLLBACK");
        }
    }
    @Test void azureDispatchNeverBindsRecoveryCatalogAndKeepsReceiptOwnership()throws Exception {
        try(var c=local.owner(db)) {
            String azure=azure().replace("sys.database_recovery_status","sys.Phase1RUnavailableBoxedCatalog");
            for(String mode:List.of("PREFLIGHT","APPLY","POSTCHECK"))run(c,azure,mode);
            assertEquals("2",value(c,"SELECT JSON_VALUE(CONVERT(nvarchar(4000),value),'$.version') FROM sys.extended_properties WHERE name='"+PROPERTY+"'"));
            assertEquals("AZURE_SQL_DATABASE_PHYSICAL:"+value(c,"SELECT physical_database_name FROM sys.databases WHERE name=DB_NAME()"),
                    value(c,"SELECT JSON_VALUE(CONVERT(nvarchar(4000),value),'$.database') FROM sys.extended_properties WHERE name='"+PROPERTY+"'"));
            String receipt=receipt(c);run(c,azure,"APPLY");assertEquals(receipt,receipt(c));
            assertEquals(58511,assertThrows(SQLException.class,()->run(c,engine,"ROLLBACK")).getErrorCode(),"Azure receipt cannot be consumed as boxed v1");
            assertEquals(receipt,receipt(c));assertEquals("4",predicates(c));
            run(c,azure,"ROLLBACK");assertNull(receipt(c));assertEquals("1",predicates(c));
        }
    }
    @Test void foreignOrMalformedReceiptNeverAuthorizesAnyEntryPoint()throws Exception {
        try(var c=local.owner(db)) {
            String azure=azure();run(c,azure,"APPLY");String original=receipt(c);
            for(String mutation:List.of("JSON_MODIFY(?, '$.database',N'AZURE_SQL_DATABASE_PHYSICAL:foreign')",
                    "JSON_MODIFY(?, '$.database',NULL)","JSON_MODIFY(?, '$.version',1)","JSON_MODIFY(?, '$.version',3)",
                    "JSON_MODIFY(?, '$.after',N'foreign')","JSON_MODIFY(?, '$.extra',1)")) {
                String changed;
                try(var p=c.prepareStatement("SELECT "+mutation)){p.setString(1,original);try(var r=p.executeQuery()){assertTrue(r.next());changed=r.getString(1);}}
                setReceipt(c,changed);
                for(String mode:List.of("PREFLIGHT","APPLY","POSTCHECK","ROLLBACK")) {
                    assertEquals(58511,assertThrows(SQLException.class,()->run(c,azure,mode)).getErrorCode(),mutation+" / "+mode);
                    assertEquals(changed,receipt(c));assertEquals("4",predicates(c));
                }
            }
            setReceipt(c,original);run(c,azure,"ROLLBACK");assertEquals("1",predicates(c));
        }
    }
    @Test void missingAmbiguousIdentityAndUnknownEngineRefuseBeforeMutation()throws Exception {
        try(var c=local.owner(db)) {
            for(String invalid:List.of(azure().replace("@Identity=physical_database_name","@Identity=CONVERT(nvarchar(128),NULL)"),
                    azure().replace("@Identity=physical_database_name","@Identity=SPACE(1)"),
                    azure().replace("@Matches=COUNT(*) OVER()","@Matches=2"),
                    azure().replace("WHERE name COLLATE","WHERE 1=0 AND name COLLATE"),
                    engine.replace("CONVERT(int,SERVERPROPERTY('EngineEdition'))","6"))) {
                for(String mode:List.of("PREFLIGHT","APPLY","POSTCHECK","ROLLBACK")) {
                    assertEquals(58515,assertThrows(SQLException.class,()->run(c,invalid,mode)).getErrorCode());
                    assertNull(receipt(c));assertEquals("1",predicates(c));
                }
            }
        }
    }
    @Test void physicalIdentityChangeAndCrossPlatformReceiptCannotBeAdopted()throws Exception {
        try(var c=local.owner(db)) {
            run(c,engine,"APPLY");String boxedReceipt=receipt(c);
            assertEquals(58511,assertThrows(SQLException.class,()->run(c,azure(),"APPLY")).getErrorCode());
            assertEquals(boxedReceipt,receipt(c));run(c,engine,"ROLLBACK");
            run(c,azure(),"APPLY");String azureReceipt=receipt(c);
            // Model a different physical incarnation without editing the stored receipt.
            String changed=azure().replace("@Identity=physical_database_name","@Identity=CONCAT(physical_database_name,N''-changed'')");
            assertEquals(58511,assertThrows(SQLException.class,()->run(c,changed,"ROLLBACK")).getErrorCode());
            assertEquals(azureReceipt,receipt(c));assertEquals("4",predicates(c));run(c,azure(),"ROLLBACK");
        }
    }
    @Test void preexistingPredicatesAreNeverClaimedOrRemoved()throws Exception {
        try(var c=local.owner(db)) {
            sql(c,"ALTER SECURITY POLICY sec.TenantFilter ADD FILTER PREDICATE sec.fn_FilterByTenant(ShaleClientId) ON dbo.Users");
            run(c,azure(),"APPLY");run(c,azure(),"ROLLBACK");assertEquals("2",predicates(c));
            sql(c,"ALTER SECURITY POLICY sec.TenantFilter ADD FILTER PREDICATE sec.fn_FilterByTenant(ShaleClientId) ON dbo.auditLog");
            sql(c,"ALTER SECURITY POLICY sec.TenantFilter ADD FILTER PREDICATE sec.fn_FilterByTenantOrGlobal(ShaleClientId) ON dbo.Statuses");
            run(c,azure(),"APPLY");assertNull(receipt(c));run(c,azure(),"ROLLBACK");assertEquals("4",predicates(c));
        }
    }
    private String azure(){return engine.replace("CONVERT(int,SERVERPROPERTY('EngineEdition'))","5");}
    private static void run(Connection c,String sql,String mode)throws SQLException {sql(c,"DECLARE @Phase1RMode varchar(10)='"+mode+"';\n"+sql);}
    private static void sql(Connection c,String text)throws SQLException {AiCaseDataLocalSqlServerTest.sql(c,text);}
    private static String predicates(Connection c)throws SQLException{return value(c,"SELECT COUNT(*) FROM sys.security_predicates");}
    private static String receipt(Connection c)throws SQLException{return value(c,"SELECT CONVERT(nvarchar(4000),value) FROM sys.extended_properties WHERE class=3 AND major_id=SCHEMA_ID('sec') AND name='"+PROPERTY+"'");}
    private static void setReceipt(Connection c,String text)throws SQLException {
        try(var p=c.prepareStatement("EXEC sys.sp_updateextendedproperty @name=N'"+PROPERTY+"',@value=?,@level0type=N'SCHEMA',@level0name=N'sec'")){p.setString(1,text);p.execute();}
    }
    private static String value(Connection c,String query)throws SQLException {
        try(var s=c.createStatement()){s.setQueryTimeout(20);try(var r=s.executeQuery(query)){if(!r.next())return null;String value=r.getString(1);assertFalse(r.next());return value;}}
    }
}
