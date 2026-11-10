package com.shale.data.security.ai;

import com.shale.core.runtime.DbSessionProvider;
import com.shale.core.security.ai.AiScope;
import org.junit.jupiter.api.*;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import static com.shale.data.security.ai.AiTenantApplicationService.Decision.*;
import static org.junit.jupiter.api.Assertions.*;

/** Disposable loopback SQL only. Actual separate logins and certificate-validated TLS. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AiApplicationLocalSqlServerTest {
    private static final String ISSUER="https://shale.test/oauth/mcp",RESOURCE="https://shale.test/mcp",CLIENT="https://vendor.test/oauth/client.json";
    private String db,prefix,password,port,trust;
    private boolean ownedDatabase;
    private final Map<String,String> secrets=new LinkedHashMap<>();
    @BeforeAll void setup() throws Exception {
        Assumptions.assumeTrue("LOCAL_DISPOSABLE_AI".equals(System.getenv("SHALE_AI_SQL_OPT_IN")),"Explicit disposable SQL opt-in required; skip is not acceptance.");
        password=System.getenv("SHALE_AI_SQL_PASSWORD");port=System.getenv("SHALE_AI_SQL_PORT");trust=System.getenv("SHALE_AI_SQL_TRUSTSTORE");
        assertNotNull(password);assertNotNull(trust);assertNotNull(port);assertTrue(port.matches("[0-9]{4,5}"));assertTrue(Integer.parseInt(port)>=1024 && Integer.parseInt(port)<=65535);
        prefix="Pr3_"+UUID.randomUUID().toString().replace("-","");
        try(Connection c=owner("master")) { for(String role:List.of("tenant","publisher","bootstrap","probe","legacy")) {
            String secret=UUID.randomUUID()+"aB9!";exec(c,"CREATE LOGIN ["+prefix+role+"] WITH PASSWORD='"+secret+"',CHECK_POLICY=OFF");secrets.put(role,secret);
        }}
    }
    @BeforeEach void fresh() throws Exception {
        db=prefix+"db";
        try(Connection c=owner("master")){exec(c,"CREATE DATABASE ["+db+"]");ownedDatabase=true;}
        try(Connection c=owner(db)) {
            for(String batch:Files.readString(Path.of("src/test/resources/ai/synthetic-prerequisites.sql")).split("(?im)^GO\\s*$"))if(!batch.isBlank())exec(c,batch);
            exec(c,Files.readString(Path.of("../docs/sql/ai-integration/phase3b1.sql")));
            exec(c,"INSERT dbo.AiIntegrationRegistration(ShaleClientId,ClientId,Issuer,Resource,Name,ClientType,ScopeMask,CreatedByUserId,UpdatedByUserId,ExpiresAt) VALUES(7,'manual-sentinel',N'https://shale.test/oauth/mcp',N'https://shale.test/mcp',N'Manual synthetic','PUBLIC',3,71,71,DATEADD(day,30,SYSUTCDATETIME()))");
            byte[] before=legacyVersion(c);
            exec(c,migration());
            assertArrayEquals(before,legacyVersion(c),"Migration must preserve manually provisioned clients byte-for-byte.");
            for(String role:secrets.keySet()) {
                exec(c,"CREATE USER ["+prefix+role+"] FOR LOGIN ["+prefix+role+"]");
                String target=switch(role){case "tenant"->"AiApplicationTenant";case "publisher"->"AiApplicationPublisher";case "bootstrap"->"AiIntegrationBootstrap";case "legacy"->"AiIntegrationRuntime";default->null;};
                if(target!=null)exec(c,"ALTER ROLE ["+target+"] ADD MEMBER ["+prefix+role+"]");
            }
            // Test-only enablement in an exact synthetic database. No production values or binding.
            exec(c,"UPDATE dbo.AiProductCapability SET Enabled=1");
            exec(c,"INSERT dbo.TenantAiIntegrationPolicy(ShaleClientId,Enabled,UpdatedByUserId) VALUES(7,1,71),(8,1,81)");
        }
    }
    @AfterEach void drop() throws Exception {if(ownedDatabase && db!=null){try(Connection c=owner("master")){exec(c,"ALTER DATABASE ["+db+"] SET SINGLE_USER WITH ROLLBACK IMMEDIATE; DROP DATABASE ["+db+"]");}db=null;ownedDatabase=false;}}
    @AfterAll void cleanup() throws Exception {if(prefix!=null)try(Connection c=owner("master")){for(String role:secrets.keySet())exec(c,"DROP LOGIN ["+prefix+role+"]");}}

    @Test void sameExactIdentityTwoTenantsIndependentApprovalAndNoAccessFromIdentity() throws Exception {
        var app=publish(null);var a=request(7,72,app);var b=request(8,82,app);
        assertEquals(a.applicationId(),b.applicationId());assertNotEquals(a.approvalId(),b.approvalId());
        assertThrows(IllegalStateException.class,()->decide(8,81,a,APPROVE,3));
        assertThrows(IllegalStateException.class,()->decide(7,72,a,APPROVE,3));
        var aa=decide(7,71,a,APPROVE,3);var bb=decide(8,81,b,APPROVE,1);
        assertNotEquals(aa.integrationId(),bb.integrationId());
        assertThrows(IllegalStateException.class,()->decide(7,71,bb,REVOKE,0));
        assertThrows(IllegalStateException.class,()->decide(8,81,aa,REDUCE,1));
        var legacy=new AiIntegrationSecurityService(provider("legacy",7,72),true,ISSUER,RESOURCE);
        assertThrows(IllegalStateException.class,()->legacy.beginConsent(aa.integrationId(),AiScope.fromMask(1),1));
        try(Connection c=owner(db)) {
            assertEquals(1,count(c,"SELECT COUNT(*) FROM dbo.AiExternalApplication"));
            assertEquals(2,count(c,"SELECT COUNT(*) FROM dbo.AiIntegrationRegistration WHERE Status='SUSPENDED'"));
            for(String table:List.of("AiDelegationGrant","AiDelegatedSession","AiOAuthCredential","AiIntegrationRedirect","AiIntegrationClientKey")) assertEquals(0,count(c,"SELECT COUNT(*) FROM dbo."+table),"Approval must not create consent or credentials: "+table);
        }
        decide(7,71,aa,REVOKE,0);
        try(Connection c=owner(db)){assertEquals(1,count(c,"SELECT COUNT(*) FROM dbo.AiTenantApplicationApproval WHERE ShaleClientId=8 AND State='APPROVED'"));}
    }
    @Test void denialReplayAndConcurrentDecisionHaveOneWinner() throws Exception {
        var app=publish(null);var a=request(7,72,app);
        var pool=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);
        try {
            List<Future<Boolean>> futures=new ArrayList<>();
            for(var decision:List.of(APPROVE,DENY)) futures.add(pool.submit(()->{start.await();try{decide(7,71,a,decision,1);return true;}catch(IllegalStateException expected){return false;}}));
            start.countDown();int winners=0;for(var f:futures)if(f.get(15,TimeUnit.SECONDS))winners++;
            assertEquals(1,winners,"Approve/deny race must serialize on tenant lock and RowVer.");
        } finally {pool.shutdownNow();}
        assertThrows(IllegalStateException.class,()->decide(7,71,a,APPROVE,1));
        var b=request(8,82,app);var denied=decide(8,81,b,DENY,0);
        assertThrows(IllegalStateException.class,()->decide(8,81,denied,APPROVE,1));
        assertNotEquals(b.approvalId(),request(8,82,app).approvalId(),"Retry creates retained distinct history.");
    }
    @Test void stalePublicationDisabledAndExpiredRequestsCannotBeApproved() throws Exception {
        var app=publish(null);var a=request(7,72,app);var updated=publish(app.rowVersion());
        assertEquals(app.applicationId(),updated.applicationId());assertEquals(2,updated.revision());
        assertThrows(IllegalStateException.class,()->request(8,82,app));
        assertThrows(IllegalStateException.class,()->decide(7,71,a,APPROVE,1));
        var b=request(8,82,updated);
        try(Connection c=owner(db)){exec(c,"UPDATE dbo.AiTenantApplicationApproval SET ExpiresAt=DATEADD(microsecond,1,CreatedAt) WHERE ApprovalId='"+b.approvalId()+"'");}
        var expired=refreshVersion(b);
        assertThrows(IllegalStateException.class,()->decide(8,81,expired,APPROVE,1));
        var disabled=publisher().publishCimd(CLIENT,new byte[32],1,true,updated.rowVersion());
        assertTrue(disabled.disabled());assertThrows(IllegalStateException.class,()->request(8,82,disabled));
    }
    @Test void failedAuditRollsBackPublicationRequestDecisionAndRevocation() throws Exception {
        try(Connection c=owner(db)){exec(c,"CREATE TRIGGER dbo.FailGlobalAudit ON dbo.GlobalControlPlaneAuditLog AFTER INSERT AS THROW 59999,'Synthetic fault',1");}
        assertThrows(IllegalStateException.class,()->publish(null));
        try(Connection c=owner(db)){assertEquals(0,count(c,"SELECT COUNT(*) FROM dbo.AiExternalApplication"));exec(c,"DROP TRIGGER dbo.FailGlobalAudit");}
        var app=publish(null);
        failAudit();assertThrows(IllegalStateException.class,()->request(7,72,app));
        try(Connection c=owner(db)){assertEquals(0,count(c,"SELECT COUNT(*) FROM dbo.AiTenantApplicationApproval"));exec(c,"DROP TRIGGER dbo.FailApprovalAudit");}
        var a=request(7,72,app);failAudit();assertThrows(IllegalStateException.class,()->decide(7,71,a,APPROVE,3));
        try(Connection c=owner(db)){assertEquals(0,count(c,"SELECT COUNT(*) FROM dbo.AiIntegrationRegistration WHERE ClientId<>'manual-sentinel'"));exec(c,"DROP TRIGGER dbo.FailApprovalAudit");}
        var approved=decide(7,71,a,APPROVE,3);seedFamily(approved,7,72);failAudit();assertThrows(IllegalStateException.class,()->decide(7,71,approved,REVOKE,0));
        try(Connection c=owner(db)){assertEquals(1,count(c,"SELECT COUNT(*) FROM dbo.AiTenantApplicationApproval WHERE State='APPROVED'"));assertEquals(1,count(c,"SELECT COUNT(*) FROM dbo.AiOAuthCredential WHERE RevokedAt IS NULL"));assertEquals(0,count(c,"SELECT COUNT(*) FROM dbo.EntityActionAuditLog WHERE Metadata LIKE '%vendor%' OR Metadata LIKE '%https%'"));}
    }
    @Test void actualLoginsHaveNoDirectReadsWritesOrForeignGrantAuthority() throws Exception {
        var app=publish(null);var a=decide(7,71,request(7,72,app),APPROVE,3);
        for(String role:List.of("tenant","publisher","bootstrap"))try(Connection c=connection(role,7,71)) {
            assertEquals(0,count(c,"SELECT COALESCE(IS_MEMBER('ShaleAdmins'),0)+IS_MEMBER('db_owner')+IS_SRVROLEMEMBER('sysadmin')"));
            for(String table:List.of("AiExternalApplication","AiTenantApplicationApproval","AiDelegationGrant"))assertThrows(SQLException.class,()->exec(c,"SELECT * FROM dbo."+table));
            assertThrows(SQLException.class,()->exec(c,"UPDATE dbo.AiTenantApplicationApproval SET State='REVOKED'"));
            assertThrows(SQLException.class,()->exec(c,"DELETE dbo.AiDelegationGrant"));
            assertThrows(SQLException.class,()->exec(c,"EXECUTE AS USER='dbo'"));
        }
        try(Connection c=owner(db)){exec(c,"GRANT SELECT,INSERT,UPDATE,DELETE ON dbo.AiTenantApplicationApproval TO ["+prefix+"probe]");}
        try(Connection c=connection("probe",8,81)){assertEquals(0,count(c,"SELECT COUNT(*) FROM dbo.AiTenantApplicationApproval"));}
        try(Connection c=connection("probe",7,71)) {
            SQLException e=assertThrows(SQLException.class,()->exec(c,"INSERT dbo.AiTenantApplicationApproval(ShaleClientId,ApplicationId,ApplicationRevision,State,RequestedScopeMask,RequestedByUserId,UpdatedByUserId,ExpiresAt) VALUES(8,'"+app.applicationId()+"',1,'PENDING',1,82,81,DATEADD(day,1,SYSUTCDATETIME()))"));assertEquals(33504,e.getErrorCode());
            assertThrows(SQLException.class,()->exec(c,"UPDATE dbo.AiTenantApplicationApproval SET ShaleClientId=8"));
            assertThrows(SQLException.class,()->exec(c,"DELETE dbo.AiTenantApplicationApproval"));
        }
        try(Connection c=connection("probe",null,null)){assertEquals(0,count(c,"SELECT COUNT(*) FROM dbo.AiTenantApplicationApproval"));}
    }
    @Test void ceilingAndRevocationPermanentlyRevokeOnlyOwnedSyntheticGrants() throws Exception {
        var app=publish(null);var a=decide(7,71,request(7,72,app),APPROVE,3);var b=decide(8,81,request(8,82,app),APPROVE,3);
        // Explicit test-only grants: the registry and approvals never manufacture user consent.
        seedFamily(a,7,72);seedFamily(b,8,82);
        var reduced=decide(7,71,a,REDUCE,1);
        assertThrows(IllegalStateException.class,()->decide(7,71,reduced,REDUCE,3));
        try(Connection c=owner(db)){assertEquals(1,count(c,"SELECT COUNT(*) FROM dbo.AiDelegationGrant WHERE ShaleClientId=7 AND RevokedAt IS NOT NULL"));assertEquals(1,count(c,"SELECT COUNT(*) FROM dbo.AiDelegationGrant WHERE ShaleClientId=8 AND RevokedAt IS NULL"));}
        try(Connection c=owner(db)) {for(String table:List.of("AiDelegatedSession","AiOAuthCredential")) {
            assertEquals(1,count(c,"SELECT COUNT(*) FROM dbo."+table+" WHERE ShaleClientId=7 AND RevokedAt IS NOT NULL"));
            assertEquals(1,count(c,"SELECT COUNT(*) FROM dbo."+table+" WHERE ShaleClientId=8 AND RevokedAt IS NULL"));
        }}
        var legacyAdmin=new AiIntegrationSecurityService(provider("legacy",7,71),true,ISSUER,RESOURCE);
        var registration=legacyAdmin.inspectRegistrations().stream().filter(r->r.integrationId().equals(a.integrationId())).findFirst().orElseThrow();
        var narrowed=legacyAdmin.changeIntegration(a.integrationId(),AiScope.fromMask(0),false,registration.rowVersion());
        assertThrows(IllegalStateException.class,()->decide(7,71,reduced,REDUCE,1),"Approval management must preserve a stricter existing integration ceiling.");
        legacyAdmin.changeIntegration(a.integrationId(),AiScope.fromMask(0),true,narrowed.rowVersion());
        var revoked=decide(7,71,reduced,REVOKE,0);assertThrows(IllegalStateException.class,()->decide(7,71,revoked,APPROVE,1));
        assertNotEquals(a.approvalId(),request(7,72,app).approvalId());
    }
    @Test void exactCaseAndFullValueCollisionChecksPreserveExternalIdentity() throws Exception {
        var app=publish(null);
        assertThrows(IllegalStateException.class,()->publish(null),"Initial publication cannot replay over an existing identity.");
        var caseDistinct=publisher().publishCimd(CLIENT.replace("client.json","Client.json"),new byte[32],1,false,null);
        assertNotEquals(app.applicationId(),caseDistinct.applicationId(),"CIMD path case must never be normalized.");
        String target="https://collision.test/oauth/client.json";
        try(Connection c=owner(db);PreparedStatement p=c.prepareStatement("INSERT dbo.AiExternalApplication(Issuer,Resource,ExternalClientId,IdentityHash,Source,MetadataDigest,MethodMask) VALUES(?,?,N'https://different.test/client.json',HASHBYTES('SHA2_256',CONVERT(varbinary(max),CONVERT(nvarchar(max),?))+0x0000+CONVERT(varbinary(max),CONVERT(nvarchar(max),?))),'CIMD',CONVERT(binary(32),0x01),1)")) {
            p.setString(1,ISSUER);p.setString(2,RESOURCE);p.setString(3,ISSUER);p.setString(4,target);p.executeUpdate();
        }
        assertThrows(IllegalStateException.class,()->publisher().publishCimd(target,new byte[32],1,false,null),"A digest match must still compare complete issuer and external identity.");
    }
    @Test void receiptsRemainIndependentAndCatalogDriftCannotBeBlessed() throws Exception {
        try(Connection c=owner(db)) {
            // Historical foundation rerun verifies its existing receipt and is a no-op.
            exec(c,Files.readString(Path.of("../docs/sql/ai-integration/phase3b1.sql")));
            exec(c,Files.readString(Path.of("../docs/sql/ai-integration/universal-pr3-verify.sql")));
            assertThrows(SQLException.class,()->exec(c,migration()));
            exec(c,"ALTER TABLE dbo.AiExternalApplication ADD Unexpected int NULL");
            assertThrows(SQLException.class,()->exec(c,Files.readString(Path.of("../docs/sql/ai-integration/universal-pr3-verify.sql"))));
        }
    }
    @Test void removedAdministratorAndConcurrentRequestReplayAreRejected() throws Exception {
        var app=publish(null);var a=request(7,72,app);
        assertThrows(IllegalStateException.class,()->request(7,72,app));
        try(Connection c=owner(db)){exec(c,"UPDATE dbo.Users SET is_admin=0 WHERE id=71");}
        assertThrows(IllegalStateException.class,()->decide(7,71,a,APPROVE,1));
        try(Connection c=owner(db)){assertEquals(1,count(c,"SELECT COUNT(*) FROM dbo.AiTenantApplicationApproval WHERE State='PENDING'"));}
    }
    private AiTenantApplicationService.Approval refreshVersion(AiTenantApplicationService.Approval a)throws Exception {
        try(Connection c=owner(db);Statement st=c.createStatement();ResultSet r=st.executeQuery("SELECT RowVer FROM dbo.AiTenantApplicationApproval WHERE ApprovalId='"+a.approvalId()+"'")) {
            assertTrue(r.next());return new AiTenantApplicationService.Approval(a.approvalId(),a.applicationId(),a.applicationRevision(),a.integrationId(),a.state(),a.ceiling(),a.expiresAt(),new AiIntegrationSecurityService.RowVersion(r.getBytes(1)));
        }
    }
    private void seedFamily(AiTenantApplicationService.Approval a,int t,int u)throws Exception {
        try(Connection c=owner(db)) {
            exec(c,"DECLARE @g uniqueidentifier=NEWID(),@s uniqueidentifier=NEWID(); INSERT dbo.AiDelegationGrant(ShaleClientId,IntegrationId,UserId,GrantId,ScopeMask,ExpiresAt) VALUES("+t+",'"+a.integrationId()+"',"+u+",@g,3,DATEADD(hour,1,SYSUTCDATETIME())); INSERT dbo.AiDelegatedSession(ShaleClientId,IntegrationId,UserId,GrantId,SessionId,ScopeMask,ExpiresAt,IdleExpiresAt) VALUES("+t+",'"+a.integrationId()+"',"+u+",@g,@s,3,DATEADD(hour,1,SYSUTCDATETIME()),DATEADD(minute,30,SYSUTCDATETIME())); INSERT dbo.AiOAuthCredential(ShaleClientId,IntegrationId,UserId,GrantId,SessionId,CredentialHash,Kind,ScopeMask,Generation,ExpiresAt) VALUES("+t+",'"+a.integrationId()+"',"+u+",@g,@s,HASHBYTES('SHA2_256',CONVERT(varbinary(16),NEWID())),'ACCESS',3,0,DATEADD(minute,1,SYSUTCDATETIME()))");
        }
    }
    private static byte[] legacyVersion(Connection c)throws SQLException {
        try(Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT RowVer FROM dbo.AiIntegrationRegistration WHERE ClientId='manual-sentinel'")){assertTrue(r.next());return r.getBytes(1);}
    }
    private void failAudit()throws Exception {try(Connection c=owner(db)){exec(c,"CREATE TRIGGER dbo.FailApprovalAudit ON dbo.EntityActionAuditLog AFTER INSERT AS THROW 59999,'Synthetic fault',1");}}
    private AiApplicationRegistryService publisher(){return new AiApplicationRegistryService(provider("publisher",null,null),ISSUER,RESOURCE);}
    private AiApplicationRegistryService.Application publish(AiIntegrationSecurityService.RowVersion v){return publisher().publishCimd(CLIENT,new byte[32],1,false,v);}
    private AiTenantApplicationService service(int t,int u){return new AiTenantApplicationService(provider("tenant",t,u));}
    private AiTenantApplicationService.Approval request(int t,int u,AiApplicationRegistryService.Application a){return service(t,u).request(a.applicationId(),a.rowVersion(),AiScope.fromMask(3));}
    private AiTenantApplicationService.Approval decide(int t,int u,AiTenantApplicationService.Approval a,AiTenantApplicationService.Decision d,int mask){return service(t,u).decide(a.approvalId(),a.rowVersion(),d,AiScope.fromMask(mask),30);}
    private DbSessionProvider provider(String role,Integer t,Integer u) {return ()->{try{return connection(role,t,u);}catch(SQLException e){throw new IllegalStateException("Synthetic connection failed",e);}};}
    private Connection owner(String database)throws SQLException{return DriverManager.getConnection(url(database),"sa",password);}
    private Connection connection(String role,Integer t,Integer u)throws SQLException {
        Connection c=DriverManager.getConnection(url(db),prefix+role,secrets.get(role));
        try(PreparedStatement p=c.prepareStatement("EXEC sys.sp_set_session_context N'ShaleClientId',?; EXEC sys.sp_set_session_context N'PrincipalUserId',?")){p.setObject(1,t);p.setObject(2,u);p.execute();}return c;
    }
    private String url(String database){return "jdbc:sqlserver://127.0.0.1:"+port+";databaseName="+database+";encrypt=true;trustServerCertificate=false;hostNameInCertificate=localhost;trustStore="+trust+";trustStorePassword=synthetic-trust;loginTimeout=5";}
    private static void exec(Connection c,String sql)throws SQLException{try(Statement s=c.createStatement()){s.execute(sql);while(s.getMoreResults()||s.getUpdateCount()!=-1){}}}
    private static int count(Connection c,String sql)throws SQLException{try(Statement s=c.createStatement();ResultSet r=s.executeQuery(sql)){assertTrue(r.next());return r.getInt(1);}}
    private static String migration()throws Exception{return Files.readString(Path.of("../docs/sql/ai-integration/universal-pr3.sql"));}
}
