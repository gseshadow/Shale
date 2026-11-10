package com.shale.data.security.ai;

import org.junit.jupiter.api.*;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Authoritative hash-gated fixture; new compatibility methods only are selected explicitly.
 * Inherits the existing nonce-owned identity/database lifecycle without changing the fixture. */
class AiApplicationConfinementLocalSqlServerTest extends AiCaseDataLocalSqlServerTest {
    @BeforeAll void applicationIdentities() throws Exception {
        try(Connection c=owner("master")) {for(String role:List.of("publisher","tenant")) {
            String secret=UUID.randomUUID()+"aB9!";
            sql(c,"CREATE LOGIN ["+prefix+"_"+role+"] WITH PASSWORD='"+secret+"',CHECK_POLICY=OFF");
            secrets.put(role,secret);
        }}
    }
    @Override String url(String database) {
        String trust=System.getenv("SHALE_AI_SQL_TRUSTSTORE");
        assertNotNull(trust,"Compatibility acceptance requires a trusted fixture certificate.");
        return "jdbc:sqlserver://127.0.0.1:"+port+";databaseName="+database+
                ";encrypt=true;trustServerCertificate=false;hostNameInCertificate=localhost;trustStore="+trust+
                ";trustStorePassword=synthetic-trust;loginTimeout=5;socketTimeout=10000";
    }
    @Test void originalGuardRejectsCombinedCatalogWithoutTouchingReceipts() throws Exception {
        attestation().verify();
        try(Connection c=owner(db)) {
            var before=receipts(c);String binding=binding(c);
            SQLException denied=assertThrows(SQLException.class,()->sql(c,Files.readString(Path.of("../docs/sql/ai-integration/universal-pr3.sql"))));
            assertEquals(59300,denied.getErrorCode());
            assertEquals(0,count(c,"SELECT COUNT(*) FROM sys.tables WHERE name IN('AiExternalApplication','AiExternalApplicationRevision','AiTenantApplicationApproval')"));
            assertEquals(before,receipts(c),"Rejected installation must leave every historical receipt unchanged.");
            assertEquals(binding,binding(c),"Rejected installation must not modify the immutable binding.");
            sql(c,Files.readString(Path.of("../docs/sql/ai-integration/verify.sql")));
        }
        attestation().verify();
    }
    @Test void preparedCompatibilityInstallsWithoutRefreshingAnyHistoricalReceipt() throws Exception {
        attestation().verify();
        try(Connection c=owner(db)) {
            var before=receipts(c);String binding=binding(c);
            sql(c,"UPDATE dbo.AiProductCapability SET Enabled=0 WHERE Id=1");
            sql(c,Files.readString(Path.of("../docs/sql/ai-integration/universal-pr3-confinement-prepare.sql")));
            assertThrows(IllegalStateException.class,()->attestation().verify(),"A prepared but incomplete registry must not obtain readiness.");
            sql(c,Files.readString(Path.of("../docs/sql/ai-integration/universal-pr3.sql")));
            sql(c,Files.readString(Path.of("../docs/sql/ai-integration/universal-pr3-authority-inventory.sql")));
            var after=receipts(c);for(var entry:before.entrySet())assertEquals(entry.getValue(),after.get(entry.getKey()),"Historical receipt changed: "+entry.getKey());
            assertEquals(binding,binding(c),"Compatibility must preserve every binding value.");
            sql(c,Files.readString(Path.of("../docs/sql/ai-integration/verify.sql")));
            sql(c,Files.readString(Path.of("../docs/sql/ai-integration/universal-pr3-confinement-verify.sql")));
            sql(c,Files.readString(Path.of("../docs/sql/ai-integration/universal-pr3-confinement-prepare.sql")));
        }
        attachApplicationRoles();
        identitiesAttestation().verify();
        attestation().verify();
        try(Connection c=owner(db)){sql(c,"UPDATE dbo.AiProductCapability SET Enabled=1 WHERE Id=1");}
        var application=publishApplication();
        var a=new AiTenantApplicationService(provider("tenant",7,72)).request(application.applicationId(),application.rowVersion(),com.shale.core.security.ai.AiScope.fromMask(3));
        var b=new AiTenantApplicationService(provider("tenant",8,82)).request(application.applicationId(),application.rowVersion(),com.shale.core.security.ai.AiScope.fromMask(3));
        var approvedA=new AiTenantApplicationService(provider("tenant",7,71)).decide(a.approvalId(),a.rowVersion(),AiTenantApplicationService.Decision.APPROVE,com.shale.core.security.ai.AiScope.fromMask(1),1);
        var approvedB=new AiTenantApplicationService(provider("tenant",8,81)).decide(b.approvalId(),b.rowVersion(),AiTenantApplicationService.Decision.APPROVE,com.shale.core.security.ai.AiScope.fromMask(3),1);
        assertNotEquals(approvedA.integrationId(),approvedB.integrationId());
        try(Connection c=owner(db)){assertEquals(2,count(c,"SELECT COUNT(*) FROM dbo.AiTenantApplicationApproval WHERE State='APPROVED'"));}
        identitiesAttestation().verify();attestation().verify();
        // Existing manual opaque-wire consent/credentials still use the unchanged proof-bound Case RPCs.
        try(var request=authenticate(token)) {
            var invocation=IntegrationInvocation.overview(request.authority(),System.nanoTime()+8_000_000_000L);
            assertTrue(new AiCaseDataAccess(source("ai"),source("ai"),request).overview(invocation,100L,DisclosureBuffer::size).size()>0);
        }
        for(String role:List.of("publisher","tenant"))try(Connection c=connection(role,7,71)) {
            for(String procedure:legacyProcedures())assertEquals(229,assertThrows(SQLException.class,()->sql(c,"EXEC dbo."+procedure)).getErrorCode(),procedure);
            assertEquals(229,assertThrows(SQLException.class,()->sql(c,"EXEC dbo.AiDataAuthenticateV1")).getErrorCode());
            assertEquals(229,assertThrows(SQLException.class,()->sql(c,"EXEC dbo.AiRegistryConfinementV1 'VERIFY'")).getErrorCode());
        }
    }
    void installCompatibility()throws Exception {
        try(Connection c=owner(db)){sql(c,"UPDATE dbo.AiProductCapability SET Enabled=0 WHERE Id=1");sql(c,Files.readString(Path.of("../docs/sql/ai-integration/universal-pr3-confinement-prepare.sql")));sql(c,Files.readString(Path.of("../docs/sql/ai-integration/universal-pr3.sql")));}
        attachApplicationRoles();
    }
    void attachApplicationRoles()throws Exception {try(Connection c=owner(db)){sql(c,"ALTER ROLE AiApplicationPublisher ADD MEMBER ["+prefix+"_publisher]; ALTER ROLE AiApplicationTenant ADD MEMBER ["+prefix+"_tenant]");}}
    AiRegistryIdentityAttestation identitiesAttestation(){return new AiRegistryIdentityAttestation(source("publisher"),source("tenant"),source("catalog"),db);}
    AiApplicationRegistryService.Application publishApplication() {
        return new AiApplicationRegistryService(()->{try{return connection("publisher",null,null);}catch(SQLException e){throw new IllegalStateException(e);}},ISSUER,RESOURCE).publishCimd("https://vendor.test/oauth/client.json",new byte[32],1,false,null);
    }
    @Test void newGraphRejectsMemberPublicColumnAndTerminalAuditBridges()throws Exception {
        installCompatibility();identitiesAttestation().verify();attestation().verify();
        String user="["+prefix+"_publisher]";
        for(String[] fault:List.of(
                new String[]{"ALTER ROLE db_datareader ADD MEMBER "+user,"ALTER ROLE db_datareader DROP MEMBER "+user},
                new String[]{"GRANT SELECT ON dbo.Cases TO "+user,"REVOKE SELECT ON dbo.Cases FROM "+user},
                new String[]{"GRANT SELECT ON dbo.Cases(Name) TO "+user,"REVOKE SELECT ON dbo.Cases(Name) FROM "+user},
                new String[]{"CREATE ROLE Pr1947Parent AUTHORIZATION dbo; GRANT SELECT ON dbo.Cases TO Pr1947Parent; ALTER ROLE Pr1947Parent ADD MEMBER "+user,"ALTER ROLE Pr1947Parent DROP MEMBER "+user+"; DROP ROLE Pr1947Parent"},
                new String[]{"EXEC(N'CREATE FUNCTION dbo.Pr1947AuditCheck() RETURNS int AS BEGIN RETURN (SELECT COUNT(*) FROM dbo.Cases) END'); ALTER TABLE dbo.GlobalControlPlaneAuditLog ADD CONSTRAINT CK_Pr1947AuditCheck CHECK(dbo.Pr1947AuditCheck()>=0)","ALTER TABLE dbo.GlobalControlPlaneAuditLog DROP CONSTRAINT CK_Pr1947AuditCheck; DROP FUNCTION dbo.Pr1947AuditCheck"},
                new String[]{"ALTER SECURITY POLICY sec.TenantFilter DROP FILTER PREDICATE ON dbo.AiTenantApplicationApproval,DROP BLOCK PREDICATE ON dbo.AiTenantApplicationApproval AFTER INSERT,DROP BLOCK PREDICATE ON dbo.AiTenantApplicationApproval AFTER UPDATE; CREATE SECURITY POLICY sec.Pr1947Policy ADD FILTER PREDICATE sec.fn_FilterByTenant(ShaleClientId) ON dbo.AiTenantApplicationApproval,ADD BLOCK PREDICATE sec.fn_FilterByTenant(ShaleClientId) ON dbo.AiTenantApplicationApproval AFTER INSERT,ADD BLOCK PREDICATE sec.fn_FilterByTenant(ShaleClientId) ON dbo.AiTenantApplicationApproval AFTER UPDATE WITH(STATE=ON,SCHEMABINDING=ON)","DROP SECURITY POLICY sec.Pr1947Policy; ALTER SECURITY POLICY sec.TenantFilter ADD FILTER PREDICATE sec.fn_FilterByTenant(ShaleClientId) ON dbo.AiTenantApplicationApproval,ADD BLOCK PREDICATE sec.fn_FilterByTenant(ShaleClientId) ON dbo.AiTenantApplicationApproval AFTER INSERT,ADD BLOCK PREDICATE sec.fn_FilterByTenant(ShaleClientId) ON dbo.AiTenantApplicationApproval AFTER UPDATE"},
                new String[]{"CREATE TRIGGER dbo.Pr1947UnexpectedGlobal ON dbo.GlobalControlPlaneAuditLog AFTER INSERT AS BEGIN EXEC sys.sp_set_session_context N'ShaleClientId',7; UPDATE dbo.Cases SET Name=N'PR1947_BRIDGE' WHERE Id=100 AND ShaleClientId=7; EXEC sys.sp_set_session_context N'ShaleClientId',NULL; END","DROP TRIGGER dbo.Pr1947UnexpectedGlobal"})) {
            try(Connection c=owner(db)){sql(c,fault[0]);}
            try {
                // Preserve the evidence that the historical scopes alone do not close these new edges.
                try(Connection c=owner(db)){sql(c,Files.readString(Path.of("../docs/sql/ai-integration/verify.sql")));sql(c,Files.readString(Path.of("../docs/sql/ai-integration/universal-pr3-verify.sql")));
                    assertThrows(SQLException.class,()->sql(c,Files.readString(Path.of("../docs/sql/ai-integration/universal-pr3-confinement-verify.sql"))));}
                assertThrows(IllegalStateException.class,()->attestation().verify());
                assertThrows(IllegalStateException.class,()->identitiesAttestation().verify());
                if(fault[0].startsWith("CREATE TRIGGER dbo.Pr1947UnexpectedGlobal")) {
                    try(Connection c=connection("publisher",null,null)) {
                        assertEquals(229,assertThrows(SQLException.class,()->sql(c,"UPDATE dbo.Cases SET Name=N'DIRECT_DENIED' WHERE Id=100")).getErrorCode());
                    }
                    // Deliberately exercise the unsafe static chain only in this disposable database.
                    // Production composition must withhold the pool when the above readiness check fails.
                    publishApplication();
                    try(Connection c=owner(db)) {
                        sql(c,"EXEC sys.sp_set_session_context N'ShaleClientId',7");
                        assertEquals(1,count(c,"SELECT COUNT(*) FROM dbo.Cases WHERE Id=100 AND Name=N'PR1947_BRIDGE'"));
                    }
                }
            }finally{try(Connection c=owner(db)){sql(c,fault[1]);}}
            identitiesAttestation().verify();attestation().verify();
        }
        try(Connection c=owner(db)){sql(c,"CREATE VIEW dbo.Pr1947Bridge AS SELECT Id,ShaleClientId,Name FROM dbo.Cases");sql(c,"GRANT SELECT ON dbo.Pr1947Bridge TO public");}
        try {assertThrows(IllegalStateException.class,()->attestation().verify());assertThrows(IllegalStateException.class,()->identitiesAttestation().verify());}
        finally{try(Connection c=owner(db)){sql(c,"DROP VIEW dbo.Pr1947Bridge");}}
        identitiesAttestation().verify();attestation().verify();
    }
    @Test void actualPublisherSessionRejectsOtherDatabaseMapping()throws Exception {
        installCompatibility();identitiesAttestation().verify();
        String other=prefix+"_other";boolean owned=false;
        try {
            try(Connection c=owner("master")){sql(c,"CREATE DATABASE ["+other+"]");owned=true;}
            try(Connection c=owner(other)){sql(c,"CREATE USER ["+prefix+"_publisher] FOR LOGIN ["+prefix+"_publisher]");}
            // Same-database catalog facts cannot substitute for the actual login's cross-DB probe.
            try(Connection c=owner(db)){sql(c,Files.readString(Path.of("../docs/sql/ai-integration/universal-pr3-confinement-verify.sql")));}
            assertThrows(IllegalStateException.class,()->identitiesAttestation().verify());
        }finally{if(owned)try(Connection c=owner("master")){sql(c,"DROP DATABASE ["+other+"]");}}
        identitiesAttestation().verify();
    }
    @Test void unsafePreparationAndFailedSealLeaveNoPartialInstallation()throws Exception {
        try(Connection c=owner(db)) {
            var before=receipts(c);String binding=binding(c);
            assertThrows(SQLException.class,()->sql(c,Files.readString(Path.of("../docs/sql/ai-integration/universal-pr3-confinement-prepare.sql"))),"Global ON must reject preparation.");
            sql(c,"UPDATE dbo.AiProductCapability SET Enabled=0 WHERE Id=1");
            sql(c,"CREATE TRIGGER dbo.Pr1947UnexpectedGlobal ON dbo.GlobalControlPlaneAuditLog AFTER INSERT AS RETURN");
            assertThrows(SQLException.class,()->sql(c,Files.readString(Path.of("../docs/sql/ai-integration/universal-pr3-confinement-prepare.sql"))));
            assertEquals(0,count(c,"SELECT COUNT(*) FROM sys.objects WHERE name='AiRegistryConfinementV1'"));
            assertEquals(before,receipts(c));assertEquals(binding,binding(c));
            sql(c,"DROP TRIGGER dbo.Pr1947UnexpectedGlobal");
            sql(c,Files.readString(Path.of("../docs/sql/ai-integration/universal-pr3-confinement-prepare.sql")));
            // Unreviewed terminal DML introduced after preparation must roll back registry DDL atomically.
            sql(c,"CREATE TRIGGER dbo.Pr1947UnexpectedGlobal ON dbo.GlobalControlPlaneAuditLog AFTER INSERT AS RETURN");
            assertThrows(SQLException.class,()->sql(c,Files.readString(Path.of("../docs/sql/ai-integration/universal-pr3.sql"))));
            assertEquals(0,count(c,"SELECT COUNT(*) FROM sys.tables WHERE name='AiExternalApplication'"));
            sql(c,"DROP TRIGGER dbo.Pr1947UnexpectedGlobal");
            String original=Files.readString(Path.of("../docs/sql/ai-integration/universal-pr3.sql"));
            String injected=original.replace("DECLARE @receipt varbinary(32)=", "EXEC(N'CREATE TRIGGER dbo.Pr1947LateAuditBridge ON dbo.GlobalControlPlaneAuditLog AFTER INSERT AS RETURN');\nDECLARE @receipt varbinary(32)=");
            assertNotEquals(original,injected);
            assertThrows(SQLException.class,()->sql(c,injected),"The end-of-transaction graph seal must reject a late dependency bridge.");
            assertEquals(0,count(c,"SELECT COUNT(*) FROM sys.tables WHERE name='AiExternalApplication'"));
            assertEquals(0,count(c,"SELECT COUNT(*) FROM sys.triggers WHERE name='Pr1947LateAuditBridge'"));
            assertEquals(0,count(c,"SELECT COUNT(*) FROM sys.extended_properties WHERE major_id=OBJECT_ID('dbo.AiRegistryConfinementV1') AND name='Shale.RegistryConfinementV1.Catalog'"));
            sql(c,original);
            for(var entry:before.entrySet())assertEquals(entry.getValue(),receipts(c).get(entry.getKey()));
        }
        attestation().verify();
    }
    @Test void authoritativeAuditFailureRollsBackBusinessMutations()throws Exception {
        installCompatibility();
        try(Connection c=owner(db)) {
            sql(c,"UPDATE dbo.AiProductCapability SET Enabled=1 WHERE Id=1");
            sql(c,"CREATE TRIGGER dbo.Pr1947GlobalFault ON dbo.GlobalControlPlaneAuditLog AFTER INSERT AS THROW 59999,'Synthetic fault',1");
        }
        try {
            assertThrows(IllegalStateException.class,this::publishApplication);
            try(Connection c=owner(db)){assertEquals(0,count(c,"SELECT COUNT(*) FROM dbo.AiExternalApplication"));}
        } finally {try(Connection c=owner(db)){sql(c,"DROP TRIGGER dbo.Pr1947GlobalFault");}}
        var application=publishApplication();
        var requester=new AiTenantApplicationService(provider("tenant",7,72));
        var admin=new AiTenantApplicationService(provider("tenant",7,71));
        var request=requester.request(application.applicationId(),application.rowVersion(),com.shale.core.security.ai.AiScope.fromMask(3));
        int registrations;
        try(Connection c=owner(db)) {
            registrations=count(c,"SELECT COUNT(*) FROM dbo.AiIntegrationRegistration");
            sql(c,"CREATE TRIGGER dbo.Pr1947ApprovalFault ON dbo.EntityActionAuditLog AFTER INSERT AS THROW 59999,'Synthetic fault',1");
        }
        try {
            assertThrows(IllegalStateException.class,()->admin.decide(request.approvalId(),request.rowVersion(),AiTenantApplicationService.Decision.APPROVE,com.shale.core.security.ai.AiScope.fromMask(1),1));
            try(Connection c=owner(db)) {
                assertEquals(registrations,count(c,"SELECT COUNT(*) FROM dbo.AiIntegrationRegistration"));
                assertEquals(1,count(c,"SELECT COUNT(*) FROM dbo.AiTenantApplicationApproval WHERE State='PENDING' AND IntegrationId IS NULL"));
            }
        } finally {try(Connection c=owner(db)){sql(c,"DROP TRIGGER dbo.Pr1947ApprovalFault");}}
        admin.decide(request.approvalId(),request.rowVersion(),AiTenantApplicationService.Decision.APPROVE,com.shale.core.security.ai.AiScope.fromMask(1),1);
        identitiesAttestation().verify();attestation().verify();
    }
    static Map<String,String> receipts(Connection c)throws SQLException {
        var values=new TreeMap<String,String>();
        try(Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT CONCAT(class,':',major_id,':',minor_id,':',name),CONVERT(varchar(max),CONVERT(varbinary(max),value),2) FROM sys.extended_properties WHERE name LIKE 'Shale.%'")) {
            while(r.next())values.put(r.getString(1),r.getString(2));
        }
        return values;
    }
    static String binding(Connection c)throws SQLException {
        try(Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT (SELECT Id,Issuer,Resource,Contract,JavaTimeZone,SqlTimeZone FROM dbo.AiCaseDataDeployment ORDER BY Id FOR JSON PATH)")){assertTrue(r.next());return r.getString(1);}
    }
}
