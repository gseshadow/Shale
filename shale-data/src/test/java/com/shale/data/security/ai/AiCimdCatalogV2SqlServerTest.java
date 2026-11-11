package com.shale.data.security.ai;

import org.junit.jupiter.api.*;
import javax.sql.DataSource;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Focused signed catalog acceptance; inherited historical tests are never selected. */
class AiCimdCatalogV2SqlServerTest extends AiCimdOAuthLocalSqlServerTest {
    String ownerLogin;
    final List<String> ownerLogins=new ArrayList<>();
    AiCimdRuntimeAttestation.CatalogBinding catalogBinding;
    @BeforeAll void catalogIdentities()throws Exception {
        try(var c=owner("master")) { for(String key:List.of("catalogv1","catalogv2")) {
            String secret=UUID.randomUUID()+"aB9!";
            sql(c,"CREATE LOGIN ["+prefix+"_"+key+"] WITH PASSWORD='"+secret+"',CHECK_POLICY=OFF; GRANT VIEW ANY DEFINITION TO ["+prefix+"_"+key+"]");secrets.put(key,secret);
        } }
    }
    @AfterEach void removeSigner()throws Exception {
        try(var c=owner("master")) {
            if(count(c,"SELECT COUNT(*) FROM sys.server_principals WHERE name='AiCimdCatalogSignerV2'")==1) sql(c,"DROP LOGIN AiCimdCatalogSignerV2");
            if(count(c,"SELECT COUNT(*) FROM sys.certificates WHERE name='AiCimdCatalogCertificateV2'")==1) sql(c,"DROP CERTIFICATE AiCimdCatalogCertificateV2");
        }
    }
    @AfterAll void removeOwners()throws Exception { try(var c=owner("master")) { for(String login:ownerLogins) sql(c,"DROP LOGIN ["+login+"]"); } }
    void foundation()throws Exception {
        try(var c=owner(db)) {
            sql(c,"UPDATE dbo.AiProductCapability SET Enabled=0; UPDATE dbo.AiCimdProofControlV1 SET Enabled=0; UPDATE dbo.AiCimdOAuthControlV1 SET Enabled=0");
            installSql(c,Files.readString(Path.of("../docs/sql/ai-integration/universal-pr4c-runtime.sql")));
            sql(c,"ALTER ROLE AiCimdCatalogV1 ADD MEMBER ["+prefix+"_catalogv1]");
            assertEquals(59620,assertThrows(SQLException.class,()->installSql(c,Files.readString(Path.of("../docs/sql/ai-integration/universal-pr4d-catalog.sql")))).getErrorCode(),"An sa-owned database is never accepted for signed server authentication.");
            assertEquals(0,count(c,"SELECT COUNT(*) FROM sys.objects WHERE name='AiCimdAttestCatalogV2'"));
        }
        try(var c=source("catalogv1").getConnection()) { assertEquals(59310,assertThrows(SQLException.class,()->installSql(c,"EXEC dbo.AiCimdAttestCatalogV1")).getErrorCode()); }
        ownerLogin=prefix+"_owner_"+UUID.randomUUID().toString().replace("-","");ownerLogins.add(ownerLogin);
        try(var c=owner("master")) {
            sql(c,"CREATE LOGIN ["+ownerLogin+"] WITH PASSWORD='"+UUID.randomUUID()+"aB9!',CHECK_POLICY=OFF; ALTER LOGIN ["+ownerLogin+"] DISABLE; ALTER AUTHORIZATION ON DATABASE::["+db+"] TO ["+ownerLogin+"]");
        }
        try(var c=owner(db)) {
            String candidate=Files.readString(Path.of("../docs/sql/ai-integration/universal-pr4d-catalog.sql"));
            String fault=candidate.replace("EXEC dbo.AiCimdAttestCatalogV2;\nCOMMIT TRANSACTION;","EXEC sys.sp_executesql N'USE master; GRANT VIEW SERVER STATE TO AiCimdCatalogSignerV2;';\nEXEC dbo.AiCimdAttestCatalogV2;\nCOMMIT TRANSACTION;");
            assertNotEquals(candidate,fault);
            assertEquals(59620,assertThrows(SQLException.class,()->installSql(c,fault)).getErrorCode());
            assertEquals(0,count(c,"SELECT COUNT(*) FROM sys.objects WHERE name='AiCimdAttestCatalogV2'"));
            assertEquals(0,count(c,"SELECT COUNT(*) FROM sys.certificates WHERE name='AiCimdCatalogCertificateV2'"));
            assertEquals(0,count(c,"SELECT COUNT(*) FROM sys.database_principals WHERE name='AiCimdCatalogV2'"));
            assertEquals(0,count(c,"SELECT COUNT(*) FROM master.sys.certificates WHERE name='AiCimdCatalogCertificateV2'"));
            assertEquals(0,count(c,"SELECT COUNT(*) FROM sys.server_principals WHERE name='AiCimdCatalogSignerV2'"));
            assertEquals(historical,receipts(c));assertEquals(immutableBinding,binding(c));
            installSql(c,candidate);
            sql(c,"ALTER ROLE AiCimdCatalogV2 ADD MEMBER ["+prefix+"_catalogv2]");
            try(var s=c.createStatement();var r=s.executeQuery("SELECT CONVERT(varchar(64),HASHBYTES('SHA2_256',CERTENCODED(CERT_ID('AiCimdCatalogCertificateV2'))),2),CONVERT(varchar(170),(SELECT owner_sid FROM sys.databases WHERE database_id=DB_ID()),2)")) { assertTrue(r.next()); catalogBinding=new AiCimdRuntimeAttestation.CatalogBinding(r.getString(1),r.getString(2)); }
            assertEquals(historical,receipts(c));assertEquals(immutableBinding,binding(c));
            assertEquals(1,count(c,"SELECT COUNT(*) FROM sys.certificates WHERE name='AiCimdCatalogCertificateV2' AND pvt_key_encryption_type='NA'"));
        }
    }
    Map<AiCimdIdentity,String> roles() { return Map.of(AiCimdIdentity.CASE,"cimd",AiCimdIdentity.BROWSER,"cimdbrowser",AiCimdIdentity.ISSUER,"cimdissuer",AiCimdIdentity.RESOLVER,"cimdresolver",AiCimdIdentity.METADATA,"cimdmetadata",AiCimdIdentity.NEUTRAL,"cimdneutral",AiCimdIdentity.REGISTRY_PUBLISHER,"publisher",AiCimdIdentity.REGISTRY_TENANT,"tenant",AiCimdIdentity.CATALOG,"catalogv2"); }
    List<DataSource> forbidden() { return List.of(source("runtime"),source("runtime"),source("bootstrap"),source("ai")); }
    AiCimdRuntimeAttestation attestor() {
        var sources=new EnumMap<AiCimdIdentity,DataSource>(AiCimdIdentity.class);var users=new EnumMap<AiCimdIdentity,String>(AiCimdIdentity.class);
        roles().forEach((role,key)->{sources.put(role,source(key));users.put(role,prefix+"_"+key);});
        return new AiCimdRuntimeAttestation(sources,users,forbidden(),db,ISSUER,RESOURCE,contract(),catalogBinding);
    }
    AiCimdRuntime.Configuration configuration() {
        var endpoints=new EnumMap<AiCimdIdentity,AiCimdRuntime.Endpoint>(AiCimdIdentity.class);
        roles().forEach((role,key)->endpoints.put(role,new AiCimdRuntime.Endpoint("jdbc:sqlserver://localhost:"+port+";databaseName="+db,prefix+"_"+key,secrets.get(key))));
        return new AiCimdRuntime.Configuration(endpoints,db,ISSUER,RESOURCE,contract(),catalogBinding);
    }
    @Test void catalogSuccessfulReadinessAndConfinedRuntime()throws Exception {
        foundation();attestor().verify();
        try(var c=source("catalogv2").getConnection()) {
            assertEquals(229,assertThrows(SQLException.class,()->sql(c,"SELECT * FROM dbo.Cases")).getErrorCode());
            assertEquals(229,assertThrows(SQLException.class,()->sql(c,"SELECT * FROM dbo.AiCimdOAuthReceiptV1")).getErrorCode());
            assertThrows(SQLException.class,()->sql(c,"ADD SIGNATURE TO dbo.AiCimdAttestCatalogV1 BY CERTIFICATE AiCimdCatalogCertificateV2"),"The catalog caller cannot reuse the signing key.");
        }
        try(var runtime=AiCimdRuntime.open(configuration(),forbidden())) {
            runtime.verify();
            try(var c=owner(db)) { sql(c,"UPDATE dbo.AiProductCapability SET Enabled=1; UPDATE dbo.AiCimdProofControlV1 SET Enabled=1; UPDATE dbo.AiCimdOAuthControlV1 SET Enabled=1"); }
            var issued=redeem(consent(7,72));
            var owner=runtime.oauth(AiCimdIdentity.RESOLVER,null).resolve(issued.refreshToken(),EXTERNAL,"REFRESH").orElseThrow();
            var rotated=runtime.oauth(AiCimdIdentity.ISSUER,owner).refresh(issued.refreshToken(),EXTERNAL,null,null,null,null);
            try(var request=runtime.authenticate(rotated.accessToken(),System.nanoTime()+30_000_000_000L)) {
                assertEquals(7,request.authority().shaleClientId());
                assertEquals(72,request.authority().actingUserId());
            }
            manualRead();
        }
        try(var c=owner(db)) { assertEquals(historical,receipts(c));assertEquals(immutableBinding,binding(c)); }
    }
    void fault(String database,String setup,String undo)throws Exception {
        try(var c=owner(database)) { sql(c,setup); }
        try { assertThrows(IllegalStateException.class,()->attestor().verify(),"Permission drift must remove readiness: "+database); }
        finally { try(var c=owner(database)) { sql(c,undo); } }
        attestor().verify();
    }
    @Test void catalogPermissionAndBindingDriftFailsClosed()throws Exception {
        foundation();attestor().verify();
        fault("master","ALTER LOGIN ["+ownerLogin+"] ENABLE","ALTER LOGIN ["+ownerLogin+"] DISABLE");
        fault("master","CREATE TRIGGER ["+prefix+"_server] ON ALL SERVER FOR ALTER_LOGIN AS SELECT 1 AS SyntheticResult","DROP TRIGGER ["+prefix+"_server] ON ALL SERVER");
        fault("master","GRANT VIEW SERVER STATE TO AiCimdCatalogSignerV2","REVOKE VIEW SERVER STATE FROM AiCimdCatalogSignerV2");
        fault("master","ALTER AUTHORIZATION ON DATABASE::["+db+"] TO sa","ALTER AUTHORIZATION ON DATABASE::["+db+"] TO ["+ownerLogin+"]");
        fault(db,"ALTER ROLE db_datareader ADD MEMBER ["+prefix+"_cimdbrowser]","ALTER ROLE db_datareader DROP MEMBER ["+prefix+"_cimdbrowser]");
        String table="Pr4d_"+prefix;
        try(var c=owner("msdb")) { sql(c,"CREATE TABLE dbo.["+table+"](Id int,Secret int); INSERT dbo.["+table+"] VALUES(1,42); GRANT SELECT ON dbo.["+table+"](Secret) TO public"); }
        try {
            try(var c=source("cimdbrowser").getConnection()) {
                c.setCatalog("msdb"); assertEquals(1,count(c,"SELECT COUNT(Secret) FROM dbo.["+table+"]"),"Actual guest/public authority is observable, not inferred from an empty filtered catalog.");
            }
            assertThrows(IllegalStateException.class,()->attestor().verify());
        } finally { try(var c=owner("msdb")) { sql(c,"DROP TABLE dbo.["+table+"]"); } }
        attestor().verify();
        fault("msdb","CREATE TABLE guest.["+table+"](Id int)","DROP TABLE guest.["+table+"]");
        fault("msdb","ALTER ROLE db_datareader ADD MEMBER guest","ALTER ROLE db_datareader DROP MEMBER guest");
        fault("msdb","CREATE USER ["+prefix+"_cimdbrowser] FOR LOGIN ["+prefix+"_cimdbrowser]","DROP USER ["+prefix+"_cimdbrowser]");
        String other=prefix+"_cross";
        try(var c=owner("master")) { sql(c,"CREATE DATABASE ["+other+"]"); }
        try {
            try(var c=owner(other)) { sql(c,"CREATE USER ["+prefix+"_cimd] FOR LOGIN ["+prefix+"_cimd]"); }
            assertThrows(IllegalStateException.class,()->attestor().verify());
        } finally { try(var c=owner("master")) { sql(c,"ALTER DATABASE ["+other+"] SET SINGLE_USER WITH ROLLBACK IMMEDIATE; DROP DATABASE ["+other+"]"); } }
        attestor().verify();
        var right=catalogBinding;
        catalogBinding=new AiCimdRuntimeAttestation.CatalogBinding("0".repeat(64),right.ownerSid());
        assertThrows(IllegalStateException.class,()->attestor().verify());
        catalogBinding=new AiCimdRuntimeAttestation.CatalogBinding(right.certificateSha256(),"0".repeat(32));
        assertThrows(IllegalStateException.class,()->attestor().verify());catalogBinding=right;
        try(var runtime=AiCimdRuntime.open(configuration(),forbidden())) {
            try(var c=owner(db)) { sql(c,"DROP SIGNATURE FROM dbo.AiCimdAttestCatalogV2 BY CERTIFICATE AiCimdCatalogCertificateV2"); }
            assertThrows(IllegalStateException.class,runtime::verify);
            assertThrows(IllegalStateException.class,runtime::verify,"A failed runtime stays closed until complete reconstruction and reattestation.");
        }
        try(var c=owner(db)) { assertEquals(historical,receipts(c));assertEquals(immutableBinding,binding(c)); }
    }

    /** Separate, explicitly opt-in next-phase diagnostic. A pass is not claimed in PR4D. */
    @Test void catalogCaseDisclosureBudgetDiagnostic()throws Exception {
        Assumptions.assumeTrue("LOCAL_DISPOSABLE_CIMD_BUDGET".equals(System.getenv("SHALE_CIMD_CASE_BUDGET_OPT_IN")));
        foundation();
        try(var runtime=AiCimdRuntime.open(configuration(),forbidden())) {
            try(var c=owner(db)) { sql(c,"UPDATE dbo.AiProductCapability SET Enabled=1; UPDATE dbo.AiCimdProofControlV1 SET Enabled=1; UPDATE dbo.AiCimdOAuthControlV1 SET Enabled=1"); }
            var issued=redeem(consent(7,72));
            try(var request=runtime.authenticate(issued.accessToken(),System.nanoTime()+30_000_000_000L)) {
                var invocation=IntegrationInvocation.overview(request.authority(),System.nanoTime()+8_000_000_000L);
                assertTrue(runtime.reader(request).overview(invocation,100L,DisclosureBuffer::size).size()>0);
            }
        }
    }

}
