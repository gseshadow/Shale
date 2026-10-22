package com.shale.data.security.ai;

import com.shale.core.runtime.DbSessionProvider;
import com.shale.core.security.ai.AiScope;
import org.junit.jupiter.api.*;
import java.net.URI;
import java.nio.file.*;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import static com.shale.data.security.ai.AiIntegrationSecurityService.*;
import static org.junit.jupiter.api.Assertions.*;

/** Explicitly SYNTHETIC real SQL. No Case schema or data is installed.
 * Uses actual distinct SQL logins, never dbo impersonation as runtime evidence. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AiSecurityLocalSqlServerTest {
    private static final String ISSUER="https://shale.test/oauth/mcp", RESOURCE="https://shale.test/mcp";
    private String port,password,db,prefix;
    private final Map<String,String> logins=new LinkedHashMap<>();
    private final Set<String> ownedDatabases=new HashSet<>();

    @BeforeAll void createLocalIdentities() throws Exception {
        Assumptions.assumeTrue("LOCAL_DISPOSABLE_AI".equals(System.getenv("SHALE_AI_SQL_OPT_IN")),"Explicit local disposable AI SQL opt-in required; skip is not acceptance.");
        port=System.getenv("SHALE_AI_SQL_PORT"); password=System.getenv("SHALE_AI_SQL_PASSWORD");
        assertNotNull(port); assertTrue(port.matches("[0-9]{4,5}"));
        assertTrue(Integer.parseInt(port)>=1024&&Integer.parseInt(port)<=65535); assertNotNull(password);
        prefix="AiB1_"+UUID.randomUUID().toString().replace("-","");
        try(Connection c=owner("master")) {
            for(String role:List.of("runtime","bootstrap","operator","verifier")) {
                String name=prefix+"_"+role, secret=UUID.randomUUID()+"aB9!";
                sql(c,"CREATE LOGIN ["+name+"] WITH PASSWORD='"+secret+"',CHECK_POLICY=OFF"); logins.put(role,secret);
            }
        }
    }

    @BeforeEach void newSyntheticDatabase() throws Exception {
        db=prefix+"_"+UUID.randomUUID().toString().replace("-","");
        try(Connection c=owner("master")) { sql(c,"CREATE DATABASE ["+db+"]"); ownedDatabases.add(db); }
        try(Connection c=owner(db)) {
            for(String batch:Files.readString(Path.of("src/test/resources/ai/synthetic-prerequisites.sql")).split("(?im)^GO\\s*$"))
                if(!batch.isBlank()) sql(c,batch);
            sql(c,migration());
            for(String role:logins.keySet()) {
                sql(c,"CREATE USER ["+prefix+"_"+role+"] FOR LOGIN ["+prefix+"_"+role+"]");
                String databaseRole=switch(role){case "runtime"->"AiIntegrationRuntime";case "bootstrap"->"AiIntegrationBootstrap";case "operator"->"AiCapabilityOperator";default->null;};
                if(databaseRole!=null)sql(c,"ALTER ROLE ["+databaseRole+"] ADD MEMBER ["+prefix+"_"+role+"]");
            }
        }
    }

    @AfterEach void dropExactSyntheticDatabase() throws Exception {
        if(db!=null&&ownedDatabases.contains(db)) {
            try(Connection c=owner("master")) { sql(c,"ALTER DATABASE ["+db+"] SET SINGLE_USER WITH ROLLBACK IMMEDIATE; DROP DATABASE ["+db+"]"); }
            ownedDatabases.remove(db); db=null;
        }
    }
    @AfterAll void dropExactSyntheticLogins() throws Exception {
        if(prefix==null)return;
        assertTrue(ownedDatabases.isEmpty(),"Do not remove identities before exact database cleanup.");
        try(Connection c=owner("master")) { for(String role:logins.keySet())sql(c,"DROP LOGIN ["+prefix+"_"+role+"]"); }
    }

    @Test void migrationDefaultsOffRepeatsAndRejectsCatalogDrift() throws Exception {
        try(Connection c=owner(db)) {
            assertEquals(0,count(c,"SELECT CONVERT(int,Enabled) FROM dbo.AiProductCapability"));
            for(String table:List.of("TenantAiIntegrationPolicy","AiIntegrationRegistration","AiDelegationGrant","AiDelegatedSession","AiOAuthCredential"))
                assertEquals(0,count(c,"SELECT COUNT(*) FROM dbo."+table));
            sql(c,migration());
            assertEquals(27,count(c,"SELECT COUNT(*) FROM sys.security_predicates WHERE OBJECT_NAME(target_object_id) IN('TenantAiIntegrationPolicy','AiIntegrationRegistration','AiIntegrationRedirect','AiIntegrationClientKey','AiConsentIntent','AiDelegationGrant','AiDelegatedSession','AiOAuthCredential','AiQuotaReservation')"));
            sql(c,"ALTER TABLE dbo.AiIntegrationRegistration ADD Unexpected int NULL");
            SQLException drift=assertThrows(SQLException.class,()->sql(c,migration())); assertEquals(58422,drift.getErrorCode());
            assertEquals(0,count(c,"SELECT CONVERT(int,Enabled) FROM dbo.AiProductCapability"));
        }
    }

    @Test void realRuntimeHasNoBypassOrDirectControlTableAuthority() throws Exception {
        try(Connection c=connection("runtime",7,71)) {
            assertEquals(0,count(c,"SELECT COALESCE(IS_MEMBER('ShaleAdmins'),0)+IS_MEMBER('db_owner')+IS_SRVROLEMEMBER('sysadmin')"));
            assertThrows(SQLException.class,()->sql(c,"SELECT * FROM dbo.AiIntegrationRegistration"));
            assertThrows(SQLException.class,()->sql(c,"UPDATE dbo.AiProductCapability SET Enabled=1"));
            assertThrows(SQLException.class,()->sql(c,"EXEC dbo.ResolveAiClientV1 'x',N'x',N'x'"));
            assertThrows(SQLException.class,()->sql(c,"ALTER PROCEDURE dbo.ResolveAiClientV1 AS SELECT 1"));
            assertThrows(SQLException.class,()->sql(c,"EXECUTE AS USER='dbo'"));
        }
    }

    @Test void tenantPolicyUsesCurrentAdminContextAndRowVersion() throws Exception {
        var admin=service(7,71); assertFalse(admin.inspectTenantPolicy().enabled()); assertNull(admin.inspectTenantPolicy().rowVersion()); Policy first=admin.setTenantEnabled(true,null);
        assertTrue(first.enabled());
        assertThrows(IllegalStateException.class,()->service(7,72).setTenantEnabled(false,first.rowVersion()));
        assertThrows(IllegalStateException.class,()->service(8,71).setTenantEnabled(false,first.rowVersion()));
        assertThrows(IllegalStateException.class,()->service(null,null).setTenantEnabled(false,null));
        Policy second=admin.setTenantEnabled(false,first.rowVersion()); assertFalse(second.enabled());
        assertThrows(IllegalStateException.class,()->admin.setTenantEnabled(true,first.rowVersion()));
        try(Connection c=owner(db)){assertEquals(2,count(c,"SELECT COUNT(*) FROM dbo.EntityActionAuditLog WHERE EntityType='TENANT_AI_POLICY'"));}
    }

    @Test void registrationCeilingAndForeignTenantAreEnforced() throws Exception {
        enabled(); var admin=service(7,71); Registration r=registration(false);
        assertEquals(1,admin.inspectRegistrations().size());
        assertEquals(0,service(8,81).inspectRegistrations().size());
        assertThrows(IllegalStateException.class,()->service(8,81).changeIntegration(r.integrationId(),AiScope.fromMask(1),false,r.rowVersion()));
        assertThrows(IllegalStateException.class,()->service(7,72).inspectRegistrations());
        admin.changeIntegration(r.integrationId(),AiScope.fromMask(1),false,r.rowVersion());
        assertThrows(IllegalStateException.class,()->service(7,72).beginConsent(r.integrationId(),AiScope.fromMask(2),1));
        try(Connection c=connection("runtime",7,71)) {
            assertThrows(SQLException.class,()->sql(c,"EXEC dbo.AiChangeIntegrationV1 '"+r.integrationId()+"',0x0000000000000000,4,0,'"+UUID.randomUUID()+"'"));
        }
    }

    @Test void selfConsentIsOneUseBoundToActorAndCeiling() throws Exception {
        enabled(); Registration r=registration(false); var user=service(7,72);
        var intent=user.beginConsent(r.integrationId(),AiScope.fromMask(1),1);
        assertThrows(IllegalStateException.class,()->service(7,73).acceptConsent(intent.nonce()));
        assertThrows(IllegalStateException.class,()->service(8,82).acceptConsent(intent.nonce()));
        Grant g=user.acceptConsent(intent.nonce()); assertEquals(AiScope.fromMask(1),g.scopes());
        assertThrows(IllegalStateException.class,()->user.acceptConsent(intent.nonce()));
        assertThrows(IllegalStateException.class,()->service(7,73).revokeGrant(g.grantId(),g.rowVersion()));
        RowVersion revoked=user.revokeGrant(g.grantId(),g.rowVersion());
        user.revokeGrant(g.grantId(),revoked);
        try(Connection c=owner(db)) {
            assertEquals(72,count(c,"SELECT UserId FROM dbo.AiDelegationGrant"));
            assertEquals(2,count(c,"SELECT COUNT(*) FROM dbo.EntityActionAuditLog WHERE EntityType='AI_DELEGATION_GRANT'"));
            assertEquals(0,count(c,"SELECT COUNT(*) FROM dbo.EntityActionAuditLog WHERE Metadata LIKE '%secret%' OR Metadata LIKE '%Synthetic client%'"));
        }
    }

    @Test void consentRacingRevokeAndScopeReductionCannotResurrectAuthority() throws Exception {
        enabled(); Registration r=registration(false); Grant g=grant(r,72); UUID sid=session(r,g,72,null);
        var verified=service(7,72).verifySession(sid,AiScope.CASES_OVERVIEW_READ);
        assertEquals(AiScope.fromMask(3),verified.effectiveScopes());
        IntegrationState reduced=service(7,71).changeIntegration(r.integrationId(),AiScope.fromMask(1),false,r.rowVersion());
        assertThrows(IllegalStateException.class,()->service(7,72).verifySession(sid,AiScope.CASES_SEARCH));
        service(7,71).changeIntegration(r.integrationId(),AiScope.fromMask(3),false,reduced.rowVersion());
        assertThrows(IllegalStateException.class,()->service(7,72).verifySession(sid,AiScope.CASES_OVERVIEW_READ));
        assertThrows(IllegalStateException.class,()->service(7,72).revokeGrant(g.grantId(),g.rowVersion()));
    }

    @Test void globalTenantIntegrationAndUserRevocationAreLiveAcrossServiceInstances() throws Exception {
        enabled(); Registration r=registration(false); Grant g=grant(r,72); UUID sid=session(r,g,72,null);
        assertNotNull(service(7,72).verifySession(sid,AiScope.CASES_SEARCH));
        var product=product(); Policy p=product.inspect(); product.setEnabled(false,p.rowVersion());
        assertThrows(IllegalStateException.class,()->service(7,72).verifySession(sid,AiScope.CASES_SEARCH));
        product.setEnabled(true,product.inspect().rowVersion());
        try(Connection c=owner(db)){sql(c,"UPDATE dbo.Users SET is_deleted=1 WHERE id=72");}
        assertThrows(IllegalStateException.class,()->service(7,72).verifySession(sid,AiScope.CASES_SEARCH));
        try(Connection c=owner(db)){sql(c,"UPDATE dbo.Users SET is_deleted=0 WHERE id=72");}
        Policy tenant=service(7,71).setTenantEnabled(false,tenantVersion());
        service(7,71).setTenantEnabled(true,tenant.rowVersion());
        assertThrows(IllegalStateException.class,()->service(7,72).verifySession(sid,AiScope.CASES_SEARCH));
        IntegrationState revoked=service(7,71).changeIntegration(r.integrationId(),AiScope.fromMask(3),true,r.rowVersion());
        service(7,71).changeIntegration(r.integrationId(),AiScope.fromMask(3),true,revoked.rowVersion());
        assertThrows(IllegalStateException.class,()->service(7,72).beginConsent(r.integrationId(),AiScope.fromMask(1),1));
    }

    @Test void typedContextRejectsSpoofedTenantUserSessionAndMissingGrant() throws Exception {
        enabled(); Registration r=registration(false); Grant g=grant(r,72); UUID sid=session(r,g,72,null);
        var context=service(7,72).verifySession(sid,AiScope.CASES_SEARCH);
        assertEquals(7,context.shaleClientId()); assertEquals(72,context.actingUserId());
        assertEquals(r.integrationId(),context.integrationId()); assertEquals(g.grantId(),context.grantId()); assertEquals(sid,context.delegatedSessionId());
        assertThrows(IllegalStateException.class,()->service(8,72).verifySession(sid,AiScope.CASES_SEARCH));
        assertThrows(IllegalStateException.class,()->service(7,73).verifySession(sid,AiScope.CASES_SEARCH));
        assertThrows(IllegalStateException.class,()->service(7,72).verifySession(UUID.randomUUID(),AiScope.CASES_SEARCH));
        try(Connection c=owner(db)) {
            assertThrows(SQLException.class,()->sql(c,"INSERT dbo.AiDelegatedSession(ShaleClientId,IntegrationId,UserId,GrantId,ScopeMask,ExpiresAt,IdleExpiresAt) VALUES(7,'"+r.integrationId()+"',72,NEWID(),1,DATEADD(hour,1,SYSUTCDATETIME()),DATEADD(minute,30,SYSUTCDATETIME()))"));
        }
    }

    @Test void keyRotationIsBoundedConcurrentAndRevocationInvalidatesSessions() throws Exception {
        enabled(); Registration r=registration(true); var admin=service(7,71);
        ClientKey k1=admin.rotateClientKey(r.integrationId(),publicKey(),90,r.rowVersion());
        assertThrows(IllegalStateException.class,()->admin.rotateClientKey(r.integrationId(),publicKey(),90,r.rowVersion()));
        ClientKey k2=admin.rotateClientKey(r.integrationId(),publicKey(),90,admin.inspectRegistrations().getFirst().rowVersion());
        assertThrows(IllegalStateException.class,()->admin.rotateClientKey(r.integrationId(),publicKey(),90,admin.inspectRegistrations().getFirst().rowVersion()));
        assertThrows(IllegalStateException.class,()->service(8,81).revokeClientKey(r.integrationId(),k2.credentialId(),k2.rowVersion()));
        assertThrows(IllegalStateException.class,()->admin.revokeClientKey(UUID.randomUUID(),k2.credentialId(),k2.rowVersion()));
        Grant g=grant(r,72); UUID sid=session(r,g,72,k2.credentialId());
        assertNotNull(service(7,72).verifySession(sid,AiScope.CASES_SEARCH));
        admin.revokeClientKey(r.integrationId(),k2.credentialId(),k2.rowVersion());
        assertThrows(IllegalStateException.class,()->service(7,72).verifySession(sid,AiScope.CASES_SEARCH));
        try(Connection c=owner(db)) {
            assertEquals(1,count(c,"SELECT COUNT(*) FROM dbo.AiIntegrationClientKey WHERE RevokedAt IS NULL AND ExpiresAt<=DATEADD(hour,24,SYSUTCDATETIME())"));
            assertEquals(0,count(c,"SELECT COUNT(*) FROM sys.columns WHERE object_id IN(OBJECT_ID('dbo.AiOAuthCredential'),OBJECT_ID('dbo.AiIntegrationClientKey')) AND (name LIKE '%Secret%' OR name LIKE '%TokenValue%' OR name LIKE '%Private%')"));
        }
    }

    @Test void expiredGrantFailsClosedAndDigestResolverRestoresItsCaller() throws Exception {
        enabled(); Registration r=registration(false); Grant g=grant(r,72); UUID sid=session(r,g,72,null);
        byte[] digest=AiSql.hash("synthetic-high-entropy-placeholder".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        try(Connection c=owner(db);PreparedStatement p=c.prepareStatement("INSERT dbo.AiOAuthCredential(ShaleClientId,IntegrationId,UserId,GrantId,SessionId,CredentialHash,Kind,ScopeMask,Generation,ExpiresAt) VALUES(7,?,72,?,?,?,'ACCESS',1,0,DATEADD(minute,5,SYSUTCDATETIME()))")) {
            p.setObject(1,r.integrationId());p.setObject(2,g.grantId());p.setObject(3,sid);p.setBytes(4,digest);p.executeUpdate();
        }
        var resolver=new AiBootstrapResolver(provider("bootstrap",null,null),ISSUER,RESOURCE);
        assertEquals(sid,resolver.resolveAccessDigest(digest).orElseThrow().sessionId());
        assertTrue(new AiBootstrapResolver(provider("bootstrap",null,null),ISSUER,"https://shale.test/wrong").resolveAccessDigest(digest).isEmpty());
        try(Connection c=connection("bootstrap",null,null)) {
            assertEquals(prefix+"_bootstrap",scalar(c,"SELECT USER_NAME()"));
            sql(c,"EXEC dbo.ResolveAiCredentialV1 0x00,N'https://shale.test/oauth/mcp',N'https://shale.test/mcp'");
            assertEquals(prefix+"_bootstrap",scalar(c,"SELECT USER_NAME()"));
            assertThrows(SQLException.class,()->sql(c,"SELECT * FROM dbo.AiOAuthCredential"));
        }
        try(Connection c=owner(db)) {
            // Fresh expired fixture row, preserving immutable production lifecycle columns.
            UUID expired=UUID.randomUUID();
            sql(c,"INSERT dbo.AiDelegationGrant(ShaleClientId,IntegrationId,UserId,GrantId,ScopeMask,ConsentedAt,ExpiresAt) VALUES(7,'"+r.integrationId()+"',72,'"+expired+"',1,DATEADD(hour,-2,SYSUTCDATETIME()),DATEADD(hour,-1,SYSUTCDATETIME()))");
            UUID expiredSid=UUID.randomUUID();
            sql(c,"INSERT dbo.AiDelegatedSession(ShaleClientId,IntegrationId,UserId,GrantId,SessionId,ScopeMask,ExpiresAt,IdleExpiresAt) VALUES(7,'"+r.integrationId()+"',72,'"+expired+"','"+expiredSid+"',1,DATEADD(hour,1,SYSUTCDATETIME()),DATEADD(minute,30,SYSUTCDATETIME()))");
            assertThrows(IllegalStateException.class,()->service(7,72).verifySession(expiredSid,AiScope.CASES_SEARCH));
        }
    }

    @Test void rlsFiltersAndBlocksForeignRowsWithoutPrivilegedRuntime() throws Exception {
        enabled(); Registration r=registration(false);
        try(Connection c=owner(db)) {
            sql(c,"GRANT SELECT,INSERT,UPDATE ON dbo.AiIntegrationRegistration TO ["+prefix+"_verifier]");
            sql(c,"GRANT SELECT,INSERT,UPDATE ON dbo.TenantAiIntegrationPolicy TO ["+prefix+"_verifier]");
        }
        try(Connection c=connection("verifier",8,81)) {
            assertEquals(0,count(c,"SELECT COUNT(*) FROM dbo.AiIntegrationRegistration"));
        }
        try(Connection c=connection("verifier",null,null)){assertEquals(0,count(c,"SELECT COUNT(*) FROM dbo.AiIntegrationRegistration"));}
        try(Connection c=connection("verifier",7,71)) {
            SQLException block=assertThrows(SQLException.class,()->sql(c,"INSERT dbo.TenantAiIntegrationPolicy(ShaleClientId,UpdatedByUserId) VALUES(8,81)"));
            assertEquals(33504,block.getErrorCode());
            assertEquals(1,count(c,"SELECT COUNT(*) FROM dbo.AiIntegrationRegistration"));
            assertThrows(SQLException.class,()->sql(c,"UPDATE dbo.AiIntegrationRegistration SET ShaleClientId=8 WHERE IntegrationId='"+r.integrationId()+"'"));
            assertEquals(1,count(c,"SELECT COUNT(*) FROM dbo.AiIntegrationRegistration WHERE ShaleClientId=7"));
            sql(c,"EXEC sys.sp_set_session_context @key=N'ShaleClientId',@value=8; EXEC sys.sp_set_session_context @key=N'PrincipalUserId',@value=81");
            assertEquals(0,count(c,"SELECT COUNT(*) FROM dbo.AiIntegrationRegistration"));
            sql(c,"EXEC sys.sp_set_session_context @key=N'ShaleClientId',@value=NULL; EXEC sys.sp_set_session_context @key=N'PrincipalUserId',@value=NULL");
            assertEquals(0,count(c,"SELECT COUNT(*) FROM dbo.AiIntegrationRegistration"));
            sql(c,"EXEC sys.sp_set_session_context @key=N'ShaleClientId',@value=7; EXEC sys.sp_set_session_context @key=N'PrincipalUserId',@value=71");
            assertEquals(1,count(c,"SELECT COUNT(*) FROM dbo.AiIntegrationRegistration"));
        }
    }

    @Test void auditFailureRollsBackMutationAndConsentConsumption() throws Exception {
        enabled(); Registration r=registration(false); var user=service(7,72); var intent=user.beginConsent(r.integrationId(),AiScope.fromMask(1),1);
        try(Connection c=owner(db)){sql(c,"CREATE TRIGGER dbo.FailSyntheticAudit ON dbo.EntityActionAuditLog INSTEAD OF INSERT AS THROW 58499,'Synthetic audit failure',1;");}
        assertThrows(IllegalStateException.class,()->user.acceptConsent(intent.nonce()));
        assertThrows(IllegalStateException.class,()->service(7,71).changeIntegration(r.integrationId(),AiScope.fromMask(0),true,r.rowVersion()));
        try(Connection c=owner(db)) {
            assertEquals(0,count(c,"SELECT COUNT(*) FROM dbo.AiDelegationGrant"));
            assertEquals(0,count(c,"SELECT COUNT(*) FROM dbo.AiConsentIntent WHERE ConsumedAt IS NOT NULL"));
            assertEquals(0,count(c,"SELECT COUNT(*) FROM dbo.AiIntegrationRegistration WHERE RevokedAt IS NOT NULL"));
            sql(c,"DROP TRIGGER dbo.FailSyntheticAudit");
        }
        assertNotNull(user.acceptConsent(intent.nonce()));
    }

    @Test void everyNewTenantTableFiltersAndBlocksAnOtherwiseValidForeignInsert() throws Exception {
        enabled();Registration r=registration(true);var admin=service(7,71);
        ClientKey k=admin.rotateClientKey(r.integrationId(),publicKey(),30,r.rowVersion());Grant g=grant(r,72);UUID sid=session(r,g,72,k.credentialId());
        var context=service(7,72).verifySession(sid,AiScope.CASES_SEARCH);assertTrue(service(7,72).reserve(context));
        String integration="'"+r.integrationId()+"'",grant="'"+g.grantId()+"'",session="'"+sid+"'";
        Map<String,String> inserts=new LinkedHashMap<>();
        inserts.put("TenantAiIntegrationPolicy","(ShaleClientId,UpdatedByUserId) VALUES(8,81)");
        inserts.put("AiIntegrationRegistration","(ShaleClientId,ClientId,Issuer,Resource,Name,ClientType,ScopeMask,CreatedByUserId,UpdatedByUserId,ExpiresAt) VALUES(7,CONVERT(varchar(36),NEWID()),N'https://shale.test/oauth/mcp',N'https://shale.test/mcp',N'Foreign fixture','PUBLIC',1,71,71,DATEADD(day,1,SYSUTCDATETIME()))");
        inserts.put("AiIntegrationRedirect","(ShaleClientId,IntegrationId,Position,RedirectUri) VALUES(7,"+integration+",5,N'https://client.test/other')");
        inserts.put("AiIntegrationClientKey","(ShaleClientId,IntegrationId,Modulus,Exponent,CreatedByUserId,ValidFrom,ExpiresAt) VALUES(7,"+integration+",CRYPT_GEN_RANDOM(256),65537,71,DATEADD(second,1,SYSUTCDATETIME()),DATEADD(day,1,SYSUTCDATETIME()))");
        inserts.put("AiDelegationGrant","(ShaleClientId,IntegrationId,UserId,ScopeMask,ExpiresAt) VALUES(7,"+integration+",72,1,DATEADD(hour,1,SYSUTCDATETIME()))");
        inserts.put("AiConsentIntent","(ShaleClientId,IntegrationId,UserId,IntentHash,ScopeMask,ExpiresAt,GrantExpiresAt) VALUES(7,"+integration+",72,CRYPT_GEN_RANDOM(32),1,DATEADD(minute,5,SYSUTCDATETIME()),DATEADD(hour,1,SYSUTCDATETIME()))");
        inserts.put("AiDelegatedSession","(ShaleClientId,IntegrationId,UserId,GrantId,ScopeMask,ExpiresAt,IdleExpiresAt) VALUES(7,"+integration+",72,"+grant+",1,DATEADD(hour,1,SYSUTCDATETIME()),DATEADD(minute,30,SYSUTCDATETIME()))");
        inserts.put("AiOAuthCredential","(ShaleClientId,IntegrationId,UserId,GrantId,SessionId,CredentialHash,Kind,ScopeMask,Generation,ExpiresAt) VALUES(7,"+integration+",72,"+grant+","+session+",CRYPT_GEN_RANDOM(32),'ACCESS',1,0,DATEADD(minute,5,SYSUTCDATETIME()))");
        inserts.put("AiQuotaReservation","(ShaleClientId,IntegrationId,UserId,GrantId,SessionId,InvocationId,Operation,RowCost,Admitted,LeaseExpiresAt) VALUES(7,"+integration+",72,"+grant+","+session+",NEWID(),'OVERVIEW',1,1,DATEADD(second,10,SYSUTCDATETIME()))");
        try(Connection c=owner(db)) {
            sql(c,"INSERT dbo.AiOAuthCredential"+inserts.get("AiOAuthCredential"));
            for(String table:inserts.keySet())sql(c,"GRANT SELECT,INSERT,UPDATE ON dbo."+table+" TO ["+prefix+"_verifier]");
        }
        for(Integer tenant:Arrays.asList(8,null))try(Connection c=connection("verifier",tenant,tenant==null?null:81)) {
            for(String table:inserts.keySet())assertEquals(0,count(c,"SELECT COUNT(*) FROM dbo."+table),table+" must hide tenant A from B/NULL.");
        }
        for(var insert:inserts.entrySet()) {
            boolean policy=insert.getKey().equals("TenantAiIntegrationPolicy");
            try(Connection c=connection("verifier",policy?7:8,policy?71:81)) {
                SQLException rejected=assertThrows(SQLException.class,()->sql(c,"INSERT dbo."+insert.getKey()+insert.getValue()));
                assertEquals(33504,rejected.getErrorCode(),insert.getKey()+" must reject foreign ownership through BLOCK, not a coincidental constraint.");
            }
        }
    }

    @Test void concurrentReservationsHaveOneWinnerAndReleaseNeverRefundsBudget() throws Exception {
        enabled(); Registration r=registration(false); UUID sid=session(r,grant(r,72),72,null);
        var s1=service(7,72);var s2=service(7,72);
        var a=s1.verifySession(sid,AiScope.CASES_SEARCH);var b=s2.verifySession(sid,AiScope.CASES_SEARCH);
        List<Boolean> outcomes=race(()->s1.reserve(a),()->s2.reserve(b));
        assertEquals(1,outcomes.stream().filter(Boolean::booleanValue).count());
        s1.release(a);s2.release(b);
        var afterRestart=service(7,72);var next=afterRestart.verifySession(sid,AiScope.CASES_SEARCH);
        assertTrue(afterRestart.reserve(next));afterRestart.release(next);
        assertThrows(IllegalStateException.class,()->afterRestart.reserve(next));
        try(Connection c=owner(db)) {
            assertEquals(20,count(c,"SELECT SUM(CONVERT(int,RowCost)) FROM dbo.AiQuotaReservation"));
            assertEquals(3,count(c,"SELECT COUNT(*) FROM dbo.AiQuotaReservation"));
        }
    }

    @Test void durableDailyRowAndSlidingMinuteBudgetsSurviveNewServiceInstances() throws Exception {
        enabled(); Registration r=registration(false); UUID sid=session(r,grant(r,72),72,null);
        for(int i=0;i<10;i++){var s=service(7,72);var x=s.verifySession(sid,AiScope.CASES_SEARCH);assertTrue(s.reserve(x));s.release(x);}
        assertFalse(service(7,72).reserve(service(7,72).verifySession(sid,AiScope.CASES_OVERVIEW_READ)));
        // A separate user is still admitted under the integration/tenant minute ceilings.
        UUID second=session(r,grant(r,73),73,null);var x=service(7,73).verifySession(second,AiScope.CASES_SEARCH);assertTrue(service(7,73).reserve(x));
        service(7,73).release(x);
    }

    @Test void concurrentScopeChangesAndRotationsRejectStaleVersions() throws Exception {
        enabled(); Registration r=registration(true);
        List<Boolean> changes=race(()->attempt(()->service(7,71).changeIntegration(r.integrationId(),AiScope.fromMask(1),false,r.rowVersion())),
                ()->attempt(()->service(7,71).changeIntegration(r.integrationId(),AiScope.fromMask(2),false,r.rowVersion())));
        assertEquals(1,changes.stream().filter(Boolean::booleanValue).count());
        var current=service(7,71).inspectRegistrations().getFirst();RSAPublicKey k1=publicKey(),k2=publicKey();
        List<Boolean> rotations=race(()->attempt(()->service(7,71).rotateClientKey(r.integrationId(),k1,2,current.rowVersion())),
                ()->attempt(()->service(7,71).rotateClientKey(r.integrationId(),k2,2,current.rowVersion())));
        assertEquals(1,rotations.stream().filter(Boolean::booleanValue).count());
    }

    @Test void dailyBudgetsApplyAcrossUsersIntegrationsAndNewProcesses() throws Exception {
        enabled(); Registration r=registration(false); UUID sid=session(r,grant(r,72),72,null);
        seedReservations(r,72,sid,20,10,3600);
        assertFalse(service(7,72).reserve(service(7,72).verifySession(sid,AiScope.CASES_OVERVIEW_READ)));
        // Integration reaches exactly 500 rows while each additional user stays below 200.
        UUID s73=session(r,grant(r,73),73,null),s74=session(r,grant(r,74),74,null);
        seedReservations(r,73,s73,15,10,3600);seedReservations(r,74,s74,15,10,3600);
        UUID s75=session(r,grant(r,75),75,null);
        assertFalse(service(7,75).reserve(service(7,75).verifySession(s75,AiScope.CASES_OVERVIEW_READ)));
        // Different integration cannot reset tenant usage; arrive at exactly 1000 rows.
        Registration r2=registration(false),r3=registration(false);
        UUID s76=session(r2,grant(r2,76),76,null),s77=session(r2,grant(r2,77),77,null),s78=session(r3,grant(r3,78),78,null);
        seedReservations(r2,76,s76,20,10,3600);seedReservations(r2,77,s77,20,10,3600);seedReservations(r3,78,s78,10,10,3600);
        UUID s79=session(r3,grant(r3,79),79,null);
        assertFalse(service(7,79).reserve(service(7,79).verifySession(s79,AiScope.CASES_OVERVIEW_READ)));
        try(Connection c=owner(db)){assertEquals(1000,count(c,"SELECT SUM(CONVERT(int,RowCost)) FROM dbo.AiQuotaReservation"));}
    }

    @Test void distributedConcurrencyCountsAllThreeOwners() throws Exception {
        enabled();Registration a=registration(false),b=registration(false),c=registration(false);
        for(int u:new int[]{72,73}) {UUID sid=session(a,grant(a,u),u,null);assertTrue(service(7,u).reserve(service(7,u).verifySession(sid,AiScope.CASES_SEARCH)));}
        UUID blocked=session(a,grant(a,74),74,null);
        assertFalse(service(7,74).reserve(service(7,74).verifySession(blocked,AiScope.CASES_SEARCH)));
        for(int u:new int[]{74,75}) {UUID sid=session(b,grant(b,u),u,null);assertTrue(service(7,u).reserve(service(7,u).verifySession(sid,AiScope.CASES_SEARCH)));}
        UUID fifth=session(c,grant(c,76),76,null);assertTrue(service(7,76).reserve(service(7,76).verifySession(fifth,AiScope.CASES_SEARCH)));
        UUID sixth=session(c,grant(c,77),77,null);assertFalse(service(7,77).reserve(service(7,77).verifySession(sixth,AiScope.CASES_SEARCH)));
    }

    @Test void slidingMinuteLimitsApplyToIntegrationAndTenant() throws Exception {
        enabled();Registration a=registration(false),b=registration(false),c=registration(false),d=registration(false);
        for(int u:new int[]{72,73}) {UUID sid=session(a,grant(a,u),u,null);seedReservations(a,u,sid,10,1,0);}
        UUID next=session(a,grant(a,74),74,null);
        assertFalse(service(7,74).reserve(service(7,74).verifySession(next,AiScope.CASES_OVERVIEW_READ)));
        for(int u:new int[]{74,75}) {UUID sid=session(b,grant(b,u),u,null);seedReservations(b,u,sid,10,1,0);}
        // The rejected attempt already consumed one call: add nineteen to reach exactly sixty.
        UUID s76=session(c,grant(c,76),76,null),s77=session(c,grant(c,77),77,null);
        seedReservations(c,76,s76,10,1,0);seedReservations(c,77,s77,9,1,0);
        UUID denied=session(d,grant(d,78),78,null);
        assertFalse(service(7,78).reserve(service(7,78).verifySession(denied,AiScope.CASES_OVERVIEW_READ)));
    }

    @Test void actualReplacementJvmSeesCommittedAccounting() throws Exception {
        enabled();Registration r=registration(false);UUID sid=session(r,grant(r,72),72,null);
        var service=service(7,72);var first=service.verifySession(sid,AiScope.CASES_SEARCH);assertTrue(service.reserve(first));service.release(first);
        ProcessBuilder child=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin","java").toString(),"-cp",
                System.getProperty("surefire.test.class.path"),RestartProbe.class.getName(),sid.toString());
        child.environment().put("AI_PROBE_URL",url(db));child.environment().put("AI_PROBE_USER",prefix+"_runtime");
        child.environment().put("AI_PROBE_PASSWORD",logins.get("runtime"));
        Process process=child.redirectErrorStream(true).start();
        assertTrue(process.waitFor(20,TimeUnit.SECONDS),"Replacement JVM exceeded its bounded local deadline.");
        assertEquals(0,process.exitValue(),"Replacement JVM must observe authoritative SQL state.");
        try(Connection c=owner(db)){assertEquals(20,count(c,"SELECT SUM(CONVERT(int,RowCost)) FROM dbo.AiQuotaReservation"));}
    }
    public static class RestartProbe {
        public static void main(String[] args) throws Exception {
            var service=new AiIntegrationSecurityService(()->{
                try {
                    Connection c=DriverManager.getConnection(System.getenv("AI_PROBE_URL"),System.getenv("AI_PROBE_USER"),System.getenv("AI_PROBE_PASSWORD"));
                    sql(c,"EXEC sys.sp_set_session_context @key=N'ShaleClientId',@value=7; EXEC sys.sp_set_session_context @key=N'PrincipalUserId',@value=72");
                    return c;
                } catch(SQLException failure){throw new IllegalStateException("Synthetic probe connection failed.");}
            },true,ISSUER,RESOURCE);
            var context=service.verifySession(UUID.fromString(args[0]),AiScope.CASES_SEARCH);
            if(!service.reserve(context))throw new AssertionError("Expected a second durably accounted reservation.");
            service.release(context);
        }
    }

    @Test void sameNonceAndSameInvocationRacesCannotDoubleSpend() throws Exception {
        enabled();Registration r=registration(false);var user=service(7,72);var intent=user.beginConsent(r.integrationId(),AiScope.fromMask(3),1);
        List<Boolean> consents=race(()->attempt(()->user.acceptConsent(intent.nonce())),()->attempt(()->service(7,72).acceptConsent(intent.nonce())));
        assertEquals(1,consents.stream().filter(Boolean::booleanValue).count());
        Grant g;
        try(Connection c=owner(db);Statement p=c.createStatement();ResultSet q=p.executeQuery("SELECT GrantId,ScopeMask,ExpiresAt,RowVer FROM dbo.AiDelegationGrant")){
            q.next();g=new Grant(AiSql.uuid(q,"GrantId"),AiScope.fromMask(3),AiSql.instant(q,"ExpiresAt"),new RowVersion(q.getBytes("RowVer")));
        }
        UUID sid=session(r,g,72,null);var context=user.verifySession(sid,AiScope.CASES_SEARCH);
        var attempts=race(()->attempt(()->{if(!user.reserve(context))throw new IllegalStateException();}),()->attempt(()->{if(!service(7,72).reserve(context))throw new IllegalStateException();}));
        assertEquals(1,attempts.stream().filter(Boolean::booleanValue).count());
        try(Connection c=owner(db)){assertEquals(1,count(c,"SELECT COUNT(*) FROM dbo.AiQuotaReservation"));}
    }

    @Test void bootstrapDefinitionAndCallerRestorationAreExactEvenOnFailure() throws Exception {
        try(Connection c=owner(db);Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT definition,execute_as_principal_id FROM sys.sql_modules WHERE object_id=OBJECT_ID('dbo.ResolveAiCredentialV1')")) {
            assertTrue(r.next());assertEquals(-2,r.getInt(2));
            String expected=migration().split("EXEC\\(N'CREATE PROCEDURE dbo.ResolveAiCredentialV1",2)[1].split("'\\);",2)[0];
            assertEquals("CREATE PROCEDURE dbo.ResolveAiCredentialV1"+expected.replace("''","'"),r.getString(1));
        }
        try(Connection c=owner(db)){sql(c,"ALTER PROCEDURE dbo.ResolveAiCredentialV1 @Digest varbinary(max),@Issuer nvarchar(max),@Resource nvarchar(max) WITH EXECUTE AS OWNER AS THROW 58499,'Injected resolver failure',1;");}
        try(Connection c=connection("bootstrap",null,null)) {
            assertThrows(SQLException.class,()->sql(c,"EXEC dbo.ResolveAiCredentialV1 0x00,N'https://shale.test/oauth/mcp',N'https://shale.test/mcp'"));
            assertEquals(prefix+"_bootstrap",scalar(c,"SELECT USER_NAME()"));
            assertEquals(0,count(c,"SELECT COALESCE(IS_MEMBER('ShaleAdmins'),0)+IS_MEMBER('db_owner')"));
        }
    }

    @Test void migrationFailureRollsBackPartialDdlAndAmbiguousAuditAllowlist() throws Exception {
        String extra=prefix+"_"+UUID.randomUUID().toString().replace("-","");
        try(Connection c=owner("master")){sql(c,"CREATE DATABASE ["+extra+"]");ownedDatabases.add(extra);}
        try(Connection c=owner(extra)) {
            for(String batch:Files.readString(Path.of("src/test/resources/ai/synthetic-prerequisites.sql")).split("(?im)^GO\\s*$"))if(!batch.isBlank())sql(c,batch);
            String injected=migration().replace("CREATE TABLE dbo.AiDelegationGrant(",
                    "IF OBJECT_ID(N'dbo.AiIntegrationClientKey') IS NULL THROW 58498,'Injection did not reach partial DDL',1; THROW 58499,'Injected partial migration failure',1;\nCREATE TABLE dbo.AiDelegationGrant(");
            SQLException failure=assertThrows(SQLException.class,()->sql(c,injected));assertEquals(58499,failure.getErrorCode());
            assertEquals(0,count(c,"SELECT COUNT(*) FROM sys.tables WHERE name LIKE 'Ai%' OR name='TenantAiIntegrationPolicy'"));
            sql(c,"ALTER TABLE dbo.EntityActionAuditLog ADD CONSTRAINT SyntheticSecondAllowlist CHECK(EntityType IN('LINK_TYPE','CASE_LINK','CASE_LINK_SHARE','USER','TASK_POLICY_CONFIGURATION'))");
            SQLException ambiguous=assertThrows(SQLException.class,()->sql(c,migration()));assertEquals(54823,ambiguous.getErrorCode());
            assertEquals(0,count(c,"SELECT COUNT(*) FROM sys.tables WHERE name LIKE 'Ai%' OR name='TenantAiIntegrationPolicy'"));
            assertEquals(1,count(c,"SELECT COUNT(*) FROM sys.check_constraints WHERE name='SyntheticSecondAllowlist'"));
        } finally {
            try(Connection c=owner("master")){sql(c,"ALTER DATABASE ["+extra+"] SET SINGLE_USER WITH ROLLBACK IMMEDIATE; DROP DATABASE ["+extra+"]");}
            ownedDatabases.remove(extra);
        }
    }

    @Test void expiredKeySessionAndConsentIntentCannotAcquireLiveContext() throws Exception {
        enabled();Registration r=registration(true);Grant g=grant(r,72);UUID expiredKey=UUID.randomUUID();
        try(Connection c=owner(db)) {
            sql(c,"INSERT dbo.AiIntegrationClientKey(ShaleClientId,IntegrationId,CredentialId,Modulus,Exponent,CreatedAt,CreatedByUserId,ValidFrom,ExpiresAt) VALUES(7,'"+r.integrationId()+"','"+expiredKey+"',CRYPT_GEN_RANDOM(256),65537,DATEADD(hour,-2,SYSUTCDATETIME()),71,DATEADD(hour,-2,SYSUTCDATETIME()),DATEADD(hour,-1,SYSUTCDATETIME()))");
        }
        UUID expiredKeySession=session(r,g,72,expiredKey);
        assertThrows(IllegalStateException.class,()->service(7,72).verifySession(expiredKeySession,AiScope.CASES_SEARCH));
        String nonce=Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[32]);
        try(Connection c=owner(db);PreparedStatement p=c.prepareStatement("INSERT dbo.AiConsentIntent(ShaleClientId,IntegrationId,UserId,IntentHash,ScopeMask,CreatedAt,ExpiresAt,GrantExpiresAt) VALUES(7,?,72,?,1,DATEADD(minute,-6,SYSUTCDATETIME()),DATEADD(minute,-1,SYSUTCDATETIME()),DATEADD(hour,1,SYSUTCDATETIME()))")) {
            p.setObject(1,r.integrationId());p.setBytes(2,AiSql.hash(new byte[32]));p.executeUpdate();
        }
        assertThrows(IllegalStateException.class,()->service(7,72).acceptConsent(nonce));
        Registration pub=registration(false);Grant pg=grant(pub,72);UUID expired=UUID.randomUUID();
        try(Connection c=owner(db)) {
            sql(c,"INSERT dbo.AiDelegatedSession(ShaleClientId,IntegrationId,UserId,GrantId,SessionId,ScopeMask,IssuedAt,ExpiresAt,IdleExpiresAt) VALUES(7,'"+pub.integrationId()+"',72,'"+pg.grantId()+"','"+expired+"',1,DATEADD(hour,-2,SYSUTCDATETIME()),DATEADD(hour,-1,SYSUTCDATETIME()),DATEADD(minute,-90,SYSUTCDATETIME()))");
        }
        assertThrows(IllegalStateException.class,()->service(7,72).verifySession(expired,AiScope.CASES_SEARCH));
    }

    @Test void sessionFamilyRevocationIsSelfBoundPermanentAndAudited() throws Exception {
        enabled();Registration r=registration(false);UUID sid=session(r,grant(r,72),72,null);
        RowVersion version;
        try(Connection c=owner(db);Statement p=c.createStatement();ResultSet rows=p.executeQuery("SELECT RowVer FROM dbo.AiDelegatedSession WHERE SessionId='"+sid+"'")){rows.next();version=new RowVersion(rows.getBytes(1));}
        assertThrows(IllegalStateException.class,()->service(7,73).revokeSession(sid,version));
        RowVersion revoked=service(7,72).revokeSession(sid,version);service(7,72).revokeSession(sid,revoked);
        assertThrows(IllegalStateException.class,()->service(7,72).verifySession(sid,AiScope.CASES_SEARCH));
        try(Connection c=owner(db)) {
            assertEquals(1,count(c,"SELECT COUNT(*) FROM dbo.EntityActionAuditLog WHERE EntityType='AI_DELEGATED_SESSION' AND Action='DEACTIVATED'"));
            assertThrows(SQLException.class,()->sql(c,"UPDATE dbo.AiDelegatedSession SET RevokedAt=NULL,RevocationReason=NULL WHERE SessionId='"+sid+"'"));
            assertThrows(SQLException.class,()->sql(c,"DELETE dbo.AiDelegatedSession WHERE SessionId='"+sid+"'"));
        }
    }

    private static final String CALLBACK="https://client.test/callback";
    private static final String VERIFIER="v".repeat(43);
    private static String opaque() { byte[] b=new byte[32];new java.security.SecureRandom().nextBytes(b);return Base64.getUrlEncoder().withoutPadding().encodeToString(b); }
    private static String challenge() { return Base64.getUrlEncoder().withoutPadding().encodeToString(AiSql.hash(VERIFIER.getBytes(java.nio.charset.StandardCharsets.US_ASCII))); }
    private AiOAuthTransactionStore oauth(int tenant,int user) { return new AiOAuthTransactionStore(()->{try{return connection("runtime",tenant,user);}catch(SQLException e){throw new IllegalStateException("Synthetic connection unavailable",e);}},true,ISSUER,RESOURCE); }
    private UUID installOAuth() throws Exception {
        try(Connection c=owner(db)) {
            // SYNTHETIC durable-session prerequisite only: not a production schema export.
            sql(c,"CREATE TABLE dbo.UserSessions(SessionId uniqueidentifier PRIMARY KEY,ShaleClientId int NOT NULL,UserId int NOT NULL,ClientType varchar(16) NOT NULL,ExpiresAt datetime2(7) NOT NULL,RevokedAt datetime2(7) NULL)");
            sql(c,Files.readString(Path.of("../docs/sql/ai-integration/phase3b2b.sql")));
            UUID sid=UUID.randomUUID();
            sql(c,"INSERT dbo.UserSessions VALUES('"+sid+"',7,72,'WEB',DATEADD(hour,2,SYSUTCDATETIME()),NULL)");
            return sid;
        }
    }
    private UUID webSession(int tenant,int user)throws Exception {
        UUID session=UUID.randomUUID();
        try(Connection c=owner(db)) {sql(c,"INSERT dbo.UserSessions VALUES('"+session+"',"+tenant+","+user+",'WEB',DATEADD(hour,2,SYSUTCDATETIME()),NULL)");}
        return session;
    }
    private UUID startOAuth(Registration r,UUID sid,String nonce) {
        UUID id=UUID.randomUUID();
        oauth(7,72).begin(id,sid,r.clientId(),CALLBACK,challenge(),"exact + & Unicode: é",AiScope.fromMask(3),nonce);
        return id;
    }
    private void issueOAuth(UUID id,UUID sid,String code) { oauth(7,72).approve(id,sid,AiScope.fromMask(1));oauth(7,72).complete(id,sid,AiScope.fromMask(1),code); }
    private AiOAuthTransactionStore.Consumed consumeOAuth(String code,UUID sid,Registration r) { return oauth(7,72).consume(code,sid,r.clientId(),CALLBACK,VERIFIER,ISSUER,RESOURCE); }

    @Test void oauthFreshClientLoaderAndSelfConsentBindEveryOwnerAndExactScopes() throws Exception {
        UUID sid=installOAuth();enabled();Registration r=registration(false);
        assertEquals(r.integrationId(),oauth(7,72).loadClient(r.clientId()).orElseThrow().integration());
        service(8,81).setTenantEnabled(true,null);
        assertTrue(oauth(8,82).loadClient(r.clientId()).isEmpty(),"Both tenants enabled: ownership alone must reject the foreign client");
        UUID foreignSession=webSession(8,82);
        assertThrows(IllegalStateException.class,()->oauth(8,82).begin(UUID.randomUUID(),foreignSession,r.clientId(),CALLBACK,challenge(),"state",AiScope.fromMask(1),opaque()));
        assertTrue(oauth(7,72).loadClient(r.clientId().toLowerCase(java.util.Locale.ROOT)).isEmpty());
        String nonce=opaque();UUID id=startOAuth(r,sid,nonce);
        assertEquals("exact + & Unicode: é",oauth(7,72).findIntent(nonce,sid).orElseThrow().clientState());
        for(int user:new int[]{71,73}) {
            UUID ownSession=webSession(7,user);
            assertTrue(oauth(7,user).findIntent(nonce,ownSession).isEmpty(),"A valid other-user WEB session must not resolve this intent");
            assertThrows(IllegalStateException.class,()->oauth(7,user).approve(id,ownSession,AiScope.fromMask(1)),"Even admin cannot manufacture another user's consent");
        }
        assertTrue(oauth(7,72).findIntent(opaque(),sid).isEmpty());
        String code=opaque();issueOAuth(id,sid,code);
        assertThrows(IllegalStateException.class,()->oauth(7,72).complete(id,sid,AiScope.fromMask(1),opaque()));
        assertEquals(AiScope.fromMask(1),consumeOAuth(code,sid,r).scopes());
        try(Connection c=owner(db)) {
            assertEquals(60,count(c,"SELECT DATEDIFF(second,IssuedAt,CodeExpiresAt) FROM dbo.AiAuthorizationTransaction"));
            assertEquals(32,count(c,"SELECT DATALENGTH(CodeHash) FROM dbo.AiAuthorizationTransaction"));
            assertEquals(72,count(c,"SELECT UserId FROM dbo.AiDelegationGrant"));
            assertEquals(1,count(c,"SELECT COUNT(*) FROM dbo.EntityActionAuditLog WHERE EntityType='AI_DELEGATION_GRANT' AND Action='CREATED' AND Metadata=N'{\"AI_SCOPE_MASK\":1}'"));
            assertEquals(0,count(c,"SELECT COUNT(*) FROM dbo.AiOAuthCredential"));
            assertEquals(0,count(c,"SELECT COUNT(*) FROM dbo.AiDelegatedSession"));
        }
    }
    @Test void oauthCodeBindingRejectsChangedClientRedirectIssuerResourceAndVerifier() throws Exception {
        UUID sid=installOAuth();enabled();Registration r=registration(false);String code=opaque();issueOAuth(startOAuth(r,sid,opaque()),sid,code);
        for(String[] values:List.of(new String[]{"other",CALLBACK,VERIFIER,ISSUER,RESOURCE},new String[]{r.clientId(),CALLBACK+"/",VERIFIER,ISSUER,RESOURCE},
                new String[]{r.clientId(),CALLBACK,"w".repeat(43),ISSUER,RESOURCE},new String[]{r.clientId(),CALLBACK,VERIFIER,ISSUER+"/",RESOURCE},
                new String[]{r.clientId(),CALLBACK,VERIFIER,ISSUER,RESOURCE+"/"}))
            assertThrows(RuntimeException.class,()->oauth(7,72).consume(code,sid,values[0],values[1],values[2],values[3],values[4]));
        assertNotNull(consumeOAuth(code,sid,r));
        assertThrows(IllegalStateException.class,()->consumeOAuth(code,sid,r));
    }
    @Test void oauthCodeConsumeRaceHasExactlyOneWinnerAndReplacementJvmRejectsReplay() throws Exception {
        UUID sid=installOAuth();enabled();Registration r=registration(false);String code=opaque();issueOAuth(startOAuth(r,sid,opaque()),sid,code);
        var outcomes=race(()->attempt(()->consumeOAuth(code,sid,r)),()->attempt(()->consumeOAuth(code,sid,r)));
        assertEquals(1,outcomes.stream().filter(Boolean::booleanValue).count(),"Atomic SQL consume must have one winner");
        ProcessBuilder child=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin","java").toString(),"-cp",System.getProperty("surefire.test.class.path"),OAuthRestartProbe.class.getName());
        child.environment().put("AI_PROBE_URL",url(db));child.environment().put("AI_PROBE_USER",prefix+"_runtime");child.environment().put("AI_PROBE_PASSWORD",logins.get("runtime"));
        child.environment().put("AI_PROBE_CODE",code);child.environment().put("AI_PROBE_SID",sid.toString());child.environment().put("AI_PROBE_CLIENT",r.clientId());
        Process process=child.redirectErrorStream(true).start();
        try { assertTrue(process.waitFor(20,TimeUnit.SECONDS),"Replacement JVM deadline");assertEquals(0,process.exitValue(),"Replacement JVM must reject replay"); }
        finally { if(process.isAlive())process.destroyForcibly(); }
        try(Connection c=owner(db)) {assertEquals(1,count(c,"SELECT COUNT(*) FROM dbo.AiAuthorizationTransaction WHERE ConsumedAt IS NOT NULL"));assertEquals(1,count(c,"SELECT COUNT(*) FROM dbo.AiDelegationGrant WHERE RevokedAt IS NULL"));}
    }
    public static class OAuthRestartProbe {
        public static void main(String[] args) throws Exception {
            var store=new AiOAuthTransactionStore(()->{
                try {Connection c=DriverManager.getConnection(System.getenv("AI_PROBE_URL"),System.getenv("AI_PROBE_USER"),System.getenv("AI_PROBE_PASSWORD"));
                    sql(c,"EXEC sys.sp_set_session_context @key=N'ShaleClientId',@value=7; EXEC sys.sp_set_session_context @key=N'PrincipalUserId',@value=72");return c;
                }catch(SQLException e){throw new IllegalStateException("Probe unavailable");}
            },true,ISSUER,RESOURCE);
            if(store.loadClient(System.getenv("AI_PROBE_CLIENT")).isEmpty()) throw new AssertionError("Live database authority unavailable in replacement JVM");
            try {store.consume(System.getenv("AI_PROBE_CODE"),UUID.fromString(System.getenv("AI_PROBE_SID")),System.getenv("AI_PROBE_CLIENT"),CALLBACK,VERIFIER,ISSUER,RESOURCE);throw new AssertionError("Replay accepted after JVM replacement");}
            catch(IllegalStateException expected) {
                Throwable cause=expected;
                while(cause!=null && !(cause instanceof SQLException))cause=cause.getCause();
                if(!(cause instanceof SQLException sql) || sql.getErrorCode()!=58502) throw expected;
            }
        }
    }
    @Test void oauthCommittedRegistrationUserTenantAndGlobalDisablePreventCompletion() throws Exception {
        UUID sid=installOAuth();enabled();Registration r=registration(false);
        String nonce=opaque();UUID id=startOAuth(r,sid,nonce);oauth(7,72).approve(id,sid,AiScope.fromMask(1));
        try(Connection c=owner(db)){sql(c,"UPDATE dbo.Users SET is_deleted=1 WHERE id=72");}
        assertThrows(IllegalStateException.class,()->oauth(7,72).complete(id,sid,AiScope.fromMask(1),opaque()));
        try(Connection c=owner(db)){sql(c,"UPDATE dbo.Users SET is_deleted=0 WHERE id=72; UPDATE dbo.ShaleClients SET IsActive=0 WHERE Id=7");}
        assertThrows(IllegalStateException.class,()->oauth(7,72).complete(id,sid,AiScope.fromMask(1),opaque()));
        try(Connection c=owner(db)){sql(c,"UPDATE dbo.ShaleClients SET IsActive=1 WHERE Id=7");}
        var product=product();product.setEnabled(false,product.inspect().rowVersion());
        assertThrows(IllegalStateException.class,()->oauth(7,72).complete(id,sid,AiScope.fromMask(1),opaque()));
        product.setEnabled(true,product.inspect().rowVersion());
        var disabled=service(7,71).setTenantEnabled(false,tenantVersion());
        assertThrows(IllegalStateException.class,()->oauth(7,72).complete(id,sid,AiScope.fromMask(1),opaque()));
        service(7,71).setTenantEnabled(true,disabled.rowVersion());
        service(7,71).changeIntegration(r.integrationId(),AiScope.fromMask(3),true,r.rowVersion());
        assertThrows(IllegalStateException.class,()->oauth(7,72).complete(id,sid,AiScope.fromMask(1),opaque()));
        try(Connection c=owner(db)){assertEquals(0,count(c,"SELECT COUNT(*) FROM dbo.AiAuthorizationTransaction WHERE CodeHash IS NOT NULL"));}
    }
    @Test void oauthGrantRevocationAndApprovalRaceNeverResurrectsRevokedAuthority() throws Exception {
        UUID sid=installOAuth();enabled();Registration r=registration(false);Grant old=grant(r,72);UUID id=startOAuth(r,sid,opaque());
        var outcomes=race(()->attempt(()->{oauth(7,72).approve(id,sid,AiScope.fromMask(1));oauth(7,72).complete(id,sid,AiScope.fromMask(1),opaque());}),
                ()->attempt(()->service(7,72).revokeGrant(old.grantId(),old.rowVersion())));
        assertTrue(outcomes.get(1),"Revocation must commit");
        try(Connection c=owner(db)){assertEquals(1,count(c,"SELECT COUNT(*) FROM dbo.AiDelegationGrant WHERE GrantId='"+old.grantId()+"' AND RevokedAt IS NOT NULL"));}
        // An intent that existed before a committed revoke cannot authorize a later completion.
        UUID stale=startOAuth(r,sid,opaque());Grant next=grant(r,72);service(7,72).revokeGrant(next.grantId(),next.rowVersion());
        oauth(7,72).approve(stale,sid,AiScope.fromMask(1));
        assertThrows(IllegalStateException.class,()->oauth(7,72).complete(stale,sid,AiScope.fromMask(1),opaque()));
    }
    @Test void oauthScopeWideningDenialAndInjectedExpiryFailClosed() throws Exception {
        UUID sid=installOAuth();enabled();Registration r=registration(false);UUID id=UUID.randomUUID();String nonce=opaque();
        oauth(7,72).begin(id,sid,r.clientId(),CALLBACK,challenge(),"state",AiScope.fromMask(1),nonce);
        assertThrows(IllegalStateException.class,()->oauth(7,72).approve(id,sid,AiScope.fromMask(2)));
        oauth(7,72).deny(id,sid);assertTrue(oauth(7,72).findIntent(nonce,sid).isEmpty());
        assertThrows(IllegalStateException.class,()->issueOAuth(id,sid,opaque()));
        String expired=opaque();UUID issued=startOAuth(r,sid,opaque());issueOAuth(issued,sid,expired);
        // Test-owner fault injection simulates elapsed database time without a 60-second sleep.
        try(Connection c=owner(db)) {
            sql(c,"DISABLE TRIGGER dbo.AiImmutable_AiAuthorizationTransaction ON dbo.AiAuthorizationTransaction");
            sql(c,"UPDATE dbo.AiAuthorizationTransaction SET IssuedAt=DATEADD(second,-120,SYSUTCDATETIME()),CodeExpiresAt=DATEADD(second,-60,SYSUTCDATETIME()) WHERE TransactionId='"+issued+"'");
            sql(c,"ENABLE TRIGGER dbo.AiImmutable_AiAuthorizationTransaction ON dbo.AiAuthorizationTransaction");
        }
        assertThrows(IllegalStateException.class,()->consumeOAuth(expired,sid,r));
        try(Connection c=owner(db)){assertEquals(0,count(c,"SELECT COUNT(*) FROM dbo.AiAuthorizationTransaction WHERE ConsumedAt IS NOT NULL"));}
    }

    @Test void oauthAuditFailureRollsBackCodeGrantAndIntentConsumptionTogether() throws Exception {
        UUID sid=installOAuth();enabled();Registration r=registration(false);String nonce=opaque();UUID id=startOAuth(r,sid,nonce);oauth(7,72).approve(id,sid,AiScope.fromMask(1));
        try(Connection c=owner(db)){sql(c,"CREATE TRIGGER dbo.FailOAuthAudit ON dbo.EntityActionAuditLog INSTEAD OF INSERT AS THROW 58499,'Synthetic audit failure',1;");}
        assertThrows(IllegalStateException.class,()->oauth(7,72).complete(id,sid,AiScope.fromMask(1),opaque()));
        try(Connection c=owner(db)) {assertEquals(0,count(c,"SELECT COUNT(*) FROM dbo.AiDelegationGrant"));assertEquals(0,count(c,"SELECT COUNT(*) FROM dbo.AiConsentIntent WHERE ConsumedAt IS NOT NULL"));assertEquals(0,count(c,"SELECT COUNT(*) FROM dbo.AiAuthorizationTransaction WHERE CodeHash IS NOT NULL"));sql(c,"DROP TRIGGER dbo.FailOAuthAudit");}
        oauth(7,72).complete(id,sid,AiScope.fromMask(1),opaque());
    }

    @Test void oauthStorageIsStrictTenantOwnedAndRuntimeCannotReadOrMutateItDirectly() throws Exception {
        UUID sid=installOAuth();enabled();Registration r=registration(false);startOAuth(r,sid,opaque());
        try(Connection c=connection("runtime",7,72)) {
            assertThrows(SQLException.class,()->sql(c,"SELECT * FROM dbo.AiAuthorizationTransaction"));
            assertThrows(SQLException.class,()->sql(c,"UPDATE dbo.AiAuthorizationTransaction SET ConsumedAt=NULL"));
        }
        try(Connection c=owner(db)) {
            assertEquals(3,count(c,"SELECT COUNT(*) FROM sys.security_predicates WHERE target_object_id=OBJECT_ID('dbo.AiAuthorizationTransaction')"));
            sql(c,"GRANT SELECT,UPDATE ON dbo.AiAuthorizationTransaction TO ["+prefix+"_verifier]");
        }
        try(Connection c=connection("verifier",8,82)) {assertEquals(0,count(c,"SELECT COUNT(*) FROM dbo.AiAuthorizationTransaction"));}
        try(Connection c=connection("verifier",7,72)) {
            assertThrows(SQLException.class,()->sql(c,"UPDATE dbo.AiAuthorizationTransaction SET ShaleClientId=8"),"Immutable-owner/BLOCK defense must reject movement");
        }
        try(Connection c=owner(db)) {assertThrows(SQLException.class,()->sql(c,"DELETE dbo.AiAuthorizationTransaction"));}
    }

    @Test void oauthConfidentialPinnedKeyAndGrantRevocationPreventCodeUse() throws Exception {
        UUID sid=installOAuth();enabled();Registration r=registration(true);
        assertTrue(oauth(7,72).loadClient(r.clientId()).isEmpty(),"Confidential registration without an active pinned key is unusable");
        var key=service(7,71).rotateClientKey(r.integrationId(),publicKey(),1,r.rowVersion());
        assertEquals(key.credentialId(),oauth(7,72).loadClient(r.clientId()).orElseThrow().keys().getFirst().id());
        String confidentialCode=opaque();issueOAuth(startOAuth(r,sid,opaque()),sid,confidentialCode);
        assertThrows(IllegalStateException.class,()->oauth(7,72).consume(confidentialCode,sid,r.clientId(),CALLBACK,"w".repeat(43),ISSUER,RESOURCE));
        assertNotNull(consumeOAuth(confidentialCode,sid,r));
        UUID id=startOAuth(r,sid,opaque());oauth(7,72).approve(id,sid,AiScope.fromMask(1));
        service(7,71).revokeClientKey(r.integrationId(),key.credentialId(),key.rowVersion());
        assertThrows(IllegalStateException.class,()->oauth(7,72).complete(id,sid,AiScope.fromMask(1),opaque()));
        Registration pub=registration(false);String code=opaque();issueOAuth(startOAuth(pub,sid,opaque()),sid,code);
        UUID grant;RowVersion version;
        try(Connection c=owner(db);var q=c.createStatement();var rows=q.executeQuery("SELECT GrantId,RowVer FROM dbo.AiDelegationGrant WHERE IntegrationId='"+pub.integrationId()+"'")){assertTrue(rows.next());grant=AiSql.uuid(rows,"GrantId");version=new RowVersion(rows.getBytes("RowVer"));}
        service(7,72).revokeGrant(grant,version);
        assertThrows(IllegalStateException.class,()->consumeOAuth(code,sid,pub));
    }
    @Test void oauthNativeLoopbackPortIsConfinedAndExactIssuedRedirectCannotChange() throws Exception {
        UUID sid=installOAuth();enabled();
        Registration r=service(7,71).register("Synthetic native",false,AiScope.fromMask(3),1,List.of(URI.create("http://127.0.0.1:3333/callback"),URI.create("http://[::1]:3333/callback")));
        for(String target:List.of("http://127.0.0.1:49152/callback","http://[::1]:49153/callback")) {
            UUID id=UUID.randomUUID();String code=opaque();oauth(7,72).begin(id,sid,r.clientId(),target,challenge(),"state",AiScope.fromMask(1),opaque());issueOAuth(id,sid,code);
            assertThrows(IllegalStateException.class,()->oauth(7,72).consume(code,sid,r.clientId(),target.replace("4915","4916"),VERIFIER,ISSUER,RESOURCE));
            assertNotNull(oauth(7,72).consume(code,sid,r.clientId(),target,VERIFIER,ISSUER,RESOURCE));
        }
        for(String target:List.of("http://127.0.0.2:49152/callback","http://127.0.0.1:49152/other","http://127.0.0.1:0/callback","http://localhost:49152/callback"))
            assertThrows(IllegalStateException.class,()->oauth(7,72).begin(UUID.randomUUID(),sid,r.clientId(),target,challenge(),"state",AiScope.fromMask(1),opaque()));
    }

    private void seedReservations(Registration r,int user,UUID sid,int amount,int rowCost,int secondsAgo)throws Exception {
        try(Connection c=owner(db);PreparedStatement p=c.prepareStatement("INSERT dbo.AiQuotaReservation(ShaleClientId,IntegrationId,UserId,GrantId,SessionId,InvocationId,Operation,RowCost,Admitted,AdmittedAt,LeaseExpiresAt,ReleasedAt) SELECT ShaleClientId,IntegrationId,UserId,GrantId,SessionId,NEWID(),?,?,1,DATEADD(second,-?,SYSUTCDATETIME()),DATEADD(second,-?,SYSUTCDATETIME()),SYSUTCDATETIME() FROM dbo.AiDelegatedSession WHERE SessionId=?")) {
            p.setString(1,rowCost==10?"SEARCH":"OVERVIEW");p.setInt(2,rowCost);p.setInt(3,secondsAgo);p.setInt(4,secondsAgo);p.setObject(5,sid);
            for(int i=0;i<amount;i++)assertEquals(1,p.executeUpdate());
        }
    }

    private void installExchange()throws Exception {
        try(var c=owner(db)) {sql(c,Files.readString(Path.of("../docs/sql/ai-integration/phase3b2c1.sql")));}
    }
    private AiOAuthExchangeStore exchange(Integer tenant,Integer user) {return new AiOAuthExchangeStore(provider("runtime",tenant,user),ISSUER,RESOURCE);}
    private AiOAuthExchangeStore bootstrapExchange() {return new AiOAuthExchangeStore(provider("bootstrap",null,null),ISSUER,RESOURCE);}
    private AiOAuthExchangeStore.ValidatedExchange exchange(String code,Registration r,UUID key,String jti) {
        var now=java.time.Instant.now().minusSeconds(1);
        return exchange(7,72).consume(code,r.clientId(),CALLBACK,VERIFIER,key,jti,key==null?null:now,key==null?null:now.plusSeconds(60));
    }
    @Test void exchangeMigrationRepeatsRejectsDriftAndRestrictsResolverAndReplayPermissions()throws Exception {
        installOAuth();installExchange(); installExchange();
        try(var c=connection("runtime",7,72)) {
            assertThrows(SQLException.class,()->sql(c,"SELECT * FROM dbo.AiAssertionReplay"));
            assertThrows(SQLException.class,()->sql(c,"EXEC dbo.ResolveAiOAuthCodeV1 0x00,'client',N'x',N'x'"));
        }
        try(var c=connection("bootstrap",null,null)) {
            assertThrows(SQLException.class,()->sql(c,"SELECT * FROM dbo.AiAssertionReplay"));
            assertThrows(SQLException.class,()->sql(c,"SELECT * FROM dbo.AiAuthorizationTransaction"));
            assertThrows(SQLException.class,()->sql(c,"EXEC dbo.AiExchangeOAuthCodeV1 0x00,'client',N'x',N'x',N'x','x',NULL,NULL,NULL,NULL,NEWID()"));
        }
        try(var c=owner(db)) {
            assertEquals(3,count(c,"SELECT COUNT(*) FROM sys.security_predicates WHERE target_object_id=OBJECT_ID('dbo.AiAssertionReplay')"));
            sql(c,"ALTER TABLE dbo.AiAssertionReplay ADD Unexpected int NULL");
            assertEquals(58600,assertThrows(SQLException.class,()->sql(c,Files.readString(Path.of("../docs/sql/ai-integration/phase3b2c1.sql")))).getErrorCode());
        }
    }
    @Test void exchangeReplayTableUsesActualNonBypassTenantFilterAndInsertBlock()throws Exception {
        UUID sid=installOAuth();installExchange();enabled();Registration r=registration(true);var key=service(7,71).rotateClientKey(r.integrationId(),publicKey(),30,r.rowVersion());
        String code=opaque();issueOAuth(startOAuth(r,sid,opaque()),sid,code);exchange(code,r,key.credentialId(),"rls-jti");
        try(var c=owner(db)){sql(c,"GRANT SELECT,INSERT ON dbo.AiAssertionReplay TO ["+prefix+"_verifier]");}
        for(Integer tenant:Arrays.asList(null,8,7))try(var c=connection("verifier",tenant,tenant==null?null:72)) {
            assertEquals(tenant!=null&&tenant==7?1:0,count(c,"SELECT COUNT(*) FROM dbo.AiAssertionReplay"));
            if(tenant==null||tenant==8)assertThrows(SQLException.class,()->sql(c,"INSERT dbo.AiAssertionReplay VALUES(7,'"+r.integrationId()+"','"+key.credentialId()+"',HASHBYTES('SHA2_256','foreign-synthetic-jti'),SYSUTCDATETIME(),DATEADD(second,30,SYSUTCDATETIME()))"),"Valid same-owner FK must still be blocked in a foreign/null runtime context");
        }
    }
    @Test void exchangePublicExactBindingsFailureDoesNotConsumeAndRaceHasOneWinner()throws Exception {
        UUID sid=installOAuth();installExchange();enabled();Registration r=registration(false);String code=opaque();UUID tx=startOAuth(r,sid,opaque());issueOAuth(tx,sid,code);
        var owner=bootstrapExchange().resolve(code,r.clientId()).orElseThrow();assertEquals(new AiOAuthExchangeStore.Owner(7,72),owner);
        assertTrue(bootstrapExchange().resolve(code,"wrong-client").isEmpty());
        for(String[] bad:List.of(new String[]{"wrong",CALLBACK,VERIFIER},new String[]{r.clientId(),CALLBACK+"/",VERIFIER},new String[]{r.clientId(),CALLBACK,"w".repeat(43)}))
            assertThrows(RuntimeException.class,()->exchange(7,72).consume(code,bad[0],bad[1],bad[2],null,null,null,null));
        assertThrows(RuntimeException.class,()->exchange(7,73).consume(code,r.clientId(),CALLBACK,VERIFIER,null,null,null,null));
        assertThrows(RuntimeException.class,()->exchange(8,82).consume(code,r.clientId(),CALLBACK,VERIFIER,null,null,null,null));
        var outcomes=race(()->attempt(()->exchange(code,r,null,null)),()->attempt(()->exchange(code,r,null,null)));
        assertEquals(1,outcomes.stream().filter(Boolean::booleanValue).count());
        assertTrue(bootstrapExchange().resolve(code,r.clientId()).isEmpty());
        assertThrows(RuntimeException.class,()->exchange(code,r,null,null));
        try(var c=owner(db)){assertEquals(0,count(c,"SELECT COUNT(*) FROM dbo.AiOAuthCredential"));assertEquals(0,count(c,"SELECT COUNT(*) FROM dbo.AiDelegatedSession"));}
    }
    @Test void exchangeAssertionReplayRaceAcrossCodesHasOneWinnerAndFailedPkceDoesNotBurnJti()throws Exception {
        UUID sid=installOAuth();installExchange();enabled();Registration r=registration(true);
        var key=service(7,71).rotateClientKey(r.integrationId(),publicKey(),30,r.rowVersion());
        String first=opaque(),second=opaque();issueOAuth(startOAuth(r,sid,opaque()),sid,first);issueOAuth(startOAuth(r,sid,opaque()),sid,second);
        var now=java.time.Instant.now().minusSeconds(1);
        assertThrows(RuntimeException.class,()->exchange(7,72).consume(first,r.clientId(),CALLBACK,"w".repeat(43),key.credentialId(),"same-jti",now,now.plusSeconds(60)));
        var outcomes=race(()->attempt(()->exchange(first,r,key.credentialId(),"same-jti")),()->attempt(()->exchange(second,r,key.credentialId(),"same-jti")));
        assertEquals(1,outcomes.stream().filter(Boolean::booleanValue).count());
        try(var c=owner(db)){assertEquals(1,count(c,"SELECT COUNT(*) FROM dbo.AiAssertionReplay"));assertEquals(1,count(c,"SELECT COUNT(*) FROM dbo.AiAuthorizationTransaction WHERE ConsumedAt IS NOT NULL"));}
        String remaining=bootstrapExchange().resolve(first,r.clientId()).isPresent()?first:second;
        assertThrows(RuntimeException.class,()->exchange(remaining,r,key.credentialId(),"same-jti"));
        replacementExchangeProbe(remaining,r,key.credentialId(),"same-jti");
        assertNotNull(exchange(remaining,r,key.credentialId(),"fresh-jti"));
        replacementExchangeProbe(first,r,key.credentialId(),"another-jti");
    }
    @org.junit.jupiter.params.ParameterizedTest(name="Exchange denies {0} committed before authorization")
    @org.junit.jupiter.params.provider.ValueSource(strings={"grant-revoke","registration-revoke","registration-expiry","key-expiry","code-expiry","web-revoke","issuer","resource"})
    void exchangeCommittedBindingExpiryOrRevocationDenies(String change)throws Exception {
        UUID sid=installOAuth();installExchange();enabled();Registration r=registration(true);var admin=service(7,71);var key=admin.rotateClientKey(r.integrationId(),publicKey(),30,r.rowVersion());
        String code=opaque();issueOAuth(startOAuth(r,sid,opaque()),sid,code);
        switch(change) {
            case "registration-revoke" -> admin.changeIntegration(r.integrationId(),AiScope.fromMask(3),true,admin.inspectRegistrations().getFirst().rowVersion());
            case "grant-revoke" -> {
                try(var c=owner(db);var st=c.createStatement();var row=st.executeQuery("SELECT GrantId,RowVer FROM dbo.AiDelegationGrant WHERE ShaleClientId=7")) {
                    assertTrue(row.next());service(7,72).revokeGrant(UUID.fromString(row.getString(1)),new RowVersion(row.getBytes(2)));
                }
            }
            case "web-revoke" -> {try(var c=owner(db)){sql(c,"UPDATE dbo.UserSessions SET RevokedAt=SYSUTCDATETIME() WHERE SessionId='"+sid+"'");}}
            case "issuer","resource" -> {
                var wrong=new AiOAuthExchangeStore(provider("runtime",7,72),change.equals("issuer")?ISSUER+"/":ISSUER,change.equals("resource")?RESOURCE+"/":RESOURCE);
                var now=java.time.Instant.now().minusSeconds(1);
                assertThrows(RuntimeException.class,()->wrong.consume(code,r.clientId(),CALLBACK,VERIFIER,key.credentialId(),"binding-jti",now,now.plusSeconds(60)));
                assertNotNull(exchange(code,r,key.credentialId(),"good-jti"));return;
            }
            default -> {
                // Explicit test-owner timestamp fault injection; not elapsed-time/production evidence.
                String table=change.equals("registration-expiry")?"AiIntegrationRegistration":change.equals("key-expiry")?"AiIntegrationClientKey":"AiAuthorizationTransaction";
                try(var c=owner(db)) {
                    sql(c,"DISABLE TRIGGER dbo.AiImmutable_"+table+" ON dbo."+table);
                    if(change.equals("code-expiry")) sql(c,"UPDATE dbo.AiAuthorizationTransaction SET IssuedAt=DATEADD(second,-120,SYSUTCDATETIME()),CodeExpiresAt=DATEADD(second,-60,SYSUTCDATETIME())");
                    else sql(c,"UPDATE dbo."+table+" SET CreatedAt=DATEADD(second,-120,SYSUTCDATETIME()),"+(change.equals("key-expiry")?"ValidFrom=DATEADD(second,-60,SYSUTCDATETIME()),":"")+"ExpiresAt=DATEADD(second,-1,SYSUTCDATETIME())");
                    sql(c,"ENABLE TRIGGER dbo.AiImmutable_"+table+" ON dbo."+table);
                }
            }
        }
        assertThrows(RuntimeException.class,()->exchange(code,r,key.credentialId(),"expired-jti"));
        try(var c=owner(db)){assertEquals(0,count(c,"SELECT COUNT(*) FROM dbo.AiAssertionReplay"));assertEquals(0,count(c,"SELECT COUNT(*) FROM dbo.AiAuthorizationTransaction WHERE ConsumedAt IS NOT NULL"));}
    }
    private void replacementExchangeProbe(String code,Registration r,UUID key,String jti)throws Exception {
        ProcessBuilder child=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin","java").toString(),"-cp",System.getProperty("surefire.test.class.path"),ExchangeRestartProbe.class.getName());
        var env=child.environment();env.put("AI_PROBE_URL",url(db));env.put("AI_PROBE_USER",prefix+"_runtime");env.put("AI_PROBE_PASSWORD",logins.get("runtime"));
        env.put("AI_PROBE_CODE",code);env.put("AI_PROBE_CLIENT",r.clientId());env.put("AI_PROBE_KEY",key.toString());env.put("AI_PROBE_JTI",jti);
        var process=child.redirectErrorStream(true).start();
        try {assertTrue(process.waitFor(20,TimeUnit.SECONDS));assertEquals(0,process.exitValue(),"Replacement JVM must reject committed replay/revocation");}
        finally {if(process.isAlive())process.destroyForcibly();}
    }
    public static class ExchangeRestartProbe {
        public static void main(String[] args)throws Exception {
            var store=new AiOAuthExchangeStore(()->{
                try {var c=DriverManager.getConnection(System.getenv("AI_PROBE_URL"),System.getenv("AI_PROBE_USER"),System.getenv("AI_PROBE_PASSWORD"));
                    sql(c,"EXEC sys.sp_set_session_context @key=N'ShaleClientId',@value=7; EXEC sys.sp_set_session_context @key=N'PrincipalUserId',@value=72");return c;
                }catch(SQLException e){throw new IllegalStateException(e);}
            },ISSUER,RESOURCE);
            var now=java.time.Instant.now().minusSeconds(1);
            try {store.consume(System.getenv("AI_PROBE_CODE"),System.getenv("AI_PROBE_CLIENT"),CALLBACK,VERIFIER,UUID.fromString(System.getenv("AI_PROBE_KEY")),System.getenv("AI_PROBE_JTI"),now,now.plusSeconds(60));throw new AssertionError("Replay accepted");}
            catch(IllegalStateException expected) {
                Throwable cause=expected;while(cause!=null && !(cause instanceof SQLException))cause=cause.getCause();
                if(!(cause instanceof SQLException sql) || !Set.of(2627,2601,58502,58602).contains(sql.getErrorCode()))throw expected;
            }
        }
    }
    @Test void exchangeAuditFailureRollsBackBothReplayAndConsume()throws Exception {
        UUID sid=installOAuth();installExchange();enabled();Registration r=registration(true);var key=service(7,71).rotateClientKey(r.integrationId(),publicKey(),30,r.rowVersion());
        String code=opaque();issueOAuth(startOAuth(r,sid,opaque()),sid,code);
        try(var c=owner(db)){sql(c,"CREATE TRIGGER dbo.DenyExchangeAudit ON dbo.EntityActionAuditLog AFTER INSERT AS THROW 58999,'Injected audit failure',1");}
        assertThrows(RuntimeException.class,()->exchange(code,r,key.credentialId(),"audit-jti"));
        try(var c=owner(db)){assertEquals(0,count(c,"SELECT COUNT(*) FROM dbo.AiAssertionReplay"));assertEquals(0,count(c,"SELECT COUNT(*) FROM dbo.AiAuthorizationTransaction WHERE ConsumedAt IS NOT NULL"));sql(c,"DROP TRIGGER dbo.DenyExchangeAudit");}
        var result=exchange(code,r,key.credentialId(),"audit-jti");assertEquals(72,result.actingUserId());assertEquals(key.credentialId(),result.authenticatingKeyId());assertEquals(AiScope.fromMask(1),result.approvedScopes());
    }
    @Test void exchangeCommittedRevocationAndEveryAuthorityDisableDenyWithoutConsumption()throws Exception {
        UUID sid=installOAuth();installExchange();enabled();Registration r=registration(true);var key=service(7,71).rotateClientKey(r.integrationId(),publicKey(),30,r.rowVersion());
        String code=opaque();issueOAuth(startOAuth(r,sid,opaque()),sid,code);
        for(String change:List.of("UPDATE dbo.Users SET is_deleted=1 WHERE id=72","UPDATE dbo.Users SET IsRemoved=1 WHERE id=72","UPDATE dbo.ShaleClients SET IsActive=0 WHERE Id=7","UPDATE dbo.AiProductCapability SET Enabled=0 WHERE Id=1","UPDATE dbo.TenantAiIntegrationPolicy SET Enabled=0 WHERE ShaleClientId=7")) {
            try(var c=owner(db)){sql(c,change);}
            assertThrows(RuntimeException.class,()->exchange(code,r,key.credentialId(),"authority-jti"),change);
            try(var c=owner(db)){sql(c,change.replace("is_deleted=1","is_deleted=0").replace("IsRemoved=1","IsRemoved=0").replace("IsActive=0","IsActive=1").replace("Enabled=0","Enabled=1"));}
        }
        // Use real administrative scope reduction: its permanent grant revoke must survive later expansion.
        var current=service(7,71).inspectRegistrations().getFirst();
        var reduced=service(7,71).changeIntegration(r.integrationId(),AiScope.fromMask(2),false,current.rowVersion());
        service(7,71).changeIntegration(r.integrationId(),AiScope.fromMask(3),false,reduced.rowVersion());
        assertThrows(RuntimeException.class,()->exchange(code,r,key.credentialId(),"authority-jti"));
        replacementExchangeProbe(code,r,key.credentialId(),"new-jti");
    }
    @Test void exchangeCommittedKeyRevokePreventsOldKeyAndReplacementKeySubstitution()throws Exception {
        UUID sid=installOAuth();installExchange();enabled();Registration r=registration(true);var admin=service(7,71);var key=admin.rotateClientKey(r.integrationId(),publicKey(),30,r.rowVersion());
        String code=opaque();issueOAuth(startOAuth(r,sid,opaque()),sid,code);
        var replacement=admin.rotateClientKey(r.integrationId(),publicKey(),30,admin.inspectRegistrations().getFirst().rowVersion());
        assertThrows(RuntimeException.class,()->exchange(code,r,replacement.credentialId(),"rotation-jti"));
        admin.revokeClientKey(r.integrationId(),key.credentialId(),admin.inspectClientKeys(r.integrationId()).stream().filter(k->k.credentialId().equals(key.credentialId())).findFirst().orElseThrow().rowVersion());
        assertThrows(RuntimeException.class,()->exchange(code,r,key.credentialId(),"old-jti"));
        replacementExchangeProbe(code,r,key.credentialId(),"restart-jti");
    }

    private void enabled() {
        var product=product();product.setEnabled(true,product.inspect().rowVersion());service(7,71).setTenantEnabled(true,null);
    }
    private AiProductCapabilityService product(){return new AiProductCapabilityService(provider("operator",null,null));}
    private AiIntegrationSecurityService service(Integer t,Integer u){return new AiIntegrationSecurityService(provider("runtime",t,u),true,ISSUER,RESOURCE);}
    private DbSessionProvider provider(String role,Integer t,Integer u){return ()->{try{return connection(role,t,u);}catch(SQLException e){throw new IllegalStateException(e);}};}
    private Connection owner(String database)throws SQLException{return DriverManager.getConnection(url(database),"sa",password);}
    private String url(String database){return "jdbc:sqlserver://127.0.0.1:"+port+";databaseName="+database+";encrypt=true;trustServerCertificate=true;loginTimeout=5";}
    private Connection connection(String role,Integer t,Integer u)throws SQLException{
        Connection c=DriverManager.getConnection(url(db),prefix+"_"+role,logins.get(role));
        try {
            sql(c,"SET QUOTED_IDENTIFIER ON; SET ANSI_NULLS ON; SET ANSI_WARNINGS ON; SET ARITHABORT ON;");
            try(PreparedStatement p=c.prepareStatement("EXEC sys.sp_set_session_context @key=N'ShaleClientId',@value=?; EXEC sys.sp_set_session_context @key=N'PrincipalUserId',@value=?")){p.setObject(1,t);p.setObject(2,u);p.execute();}
            return c;
        }catch(SQLException e){c.close();throw e;}
    }
    private Registration registration(boolean confidential){return service(7,71).register("Synthetic client",confidential,AiScope.fromMask(3),30,List.of(URI.create("https://client.test/callback")));}
    private Grant grant(Registration r,int user){var service=service(7,user);return service.acceptConsent(service.beginConsent(r.integrationId(),AiScope.fromMask(3),2).nonce());}
    private UUID session(Registration r,Grant g,int user,UUID key)throws Exception {
        UUID sid=UUID.randomUUID();
        try(Connection c=owner(db);PreparedStatement p=c.prepareStatement("INSERT dbo.AiDelegatedSession(ShaleClientId,IntegrationId,UserId,GrantId,SessionId,CredentialId,ScopeMask,ExpiresAt,IdleExpiresAt) VALUES(7,?,?,?,?,?,3,DATEADD(hour,1,SYSUTCDATETIME()),DATEADD(minute,30,SYSUTCDATETIME()))")) {
            p.setObject(1,r.integrationId());p.setInt(2,user);p.setObject(3,g.grantId());p.setObject(4,sid);p.setObject(5,key);p.executeUpdate();
        }
        return sid;
    }
    private RowVersion tenantVersion()throws Exception{try(Connection c=owner(db);Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT RowVer FROM dbo.TenantAiIntegrationPolicy WHERE ShaleClientId=7")){r.next();return new RowVersion(r.getBytes(1));}}
    private RSAPublicKey publicKey()throws Exception{var g=KeyPairGenerator.getInstance("RSA");g.initialize(2048);return (RSAPublicKey)g.generateKeyPair().getPublic();}
    private String migration()throws Exception{return Files.readString(Path.of("../docs/sql/ai-integration/phase3b1.sql"));}
    private static void sql(Connection c,String sql)throws SQLException{try(Statement s=c.createStatement()){s.setQueryTimeout(10);boolean more=s.execute(sql);while(more||s.getUpdateCount()!=-1){if(more)s.getResultSet().close();more=s.getMoreResults();}}}
    private static int count(Connection c,String sql)throws SQLException{return Integer.parseInt(scalar(c,sql));}
    private static String scalar(Connection c,String sql)throws SQLException{try(Statement s=c.createStatement();ResultSet r=s.executeQuery(sql)){assertTrue(r.next());return r.getString(1);}}
    private static boolean attempt(ThrowingRunnable task)throws Exception{try{task.run();return true;}catch(IllegalStateException expected){return false;}}
    private interface ThrowingRunnable{void run()throws Exception;}
    private static List<Boolean> race(Callable<Boolean> a,Callable<Boolean> b)throws Exception {
        CountDownLatch ready=new CountDownLatch(2),start=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var x=pool.submit(()->{ready.countDown();start.await();return a.call();});
            var y=pool.submit(()->{ready.countDown();start.await();return b.call();});
            assertTrue(ready.await(5,TimeUnit.SECONDS));start.countDown();return List.of(x.get(15,TimeUnit.SECONDS),y.get(15,TimeUnit.SECONDS));
        }
    }
    private void installCredentials()throws Exception {installExchange();try(var c=owner(db)){sql(c,Files.readString(Path.of("../docs/sql/ai-integration/phase3b2c2.sql")));}}
    private AiOAuthCredentialStore credentials() {return new AiOAuthCredentialStore(provider("runtime",7,72),ISSUER,RESOURCE);}
    private AiOAuthCredentialStore.Tokens redeem(String code,Registration r) {return credentials().redeem(code,r.clientId(),CALLBACK,VERIFIER,null,null,null,null);}
    private AiOAuthCredentialStore.Tokens refresh(String token,Registration r) {return credentials().refresh(token,r.clientId(),null,null,null,null);}
    private record CredentialFixture(Registration registration,String code,UUID web) {}
    private CredentialFixture credentialFixture()throws Exception {
        UUID web=installOAuth();installCredentials();enabled();Registration r=registration(false);
        String code=opaque();issueOAuth(startOAuth(r,web,opaque()),web,code);return new CredentialFixture(r,code,web);
    }
    @Test void credentialMigrationRepeatAndCatalogDriftFailClosed()throws Exception {
        installOAuth();installCredentials();
        try(var c=owner(db)) {
            sql(c,Files.readString(Path.of("../docs/sql/ai-integration/phase3b2c2.sql")));
            sql(c,"ALTER TABLE dbo.AiOAuthCredential ADD Unexpected int NULL");
            assertEquals(58700,assertThrows(SQLException.class,()->sql(c,Files.readString(Path.of("../docs/sql/ai-integration/phase3b2c2.sql")))).getErrorCode());
        }
    }
    @Test void credentialInitialRaceOneFamilyAndHashOnlyFiveMinuteLifetime()throws Exception {
        var f=credentialFixture();
        var outcomes=race(()->attempt(()->redeem(f.code(),f.registration())),()->attempt(()->redeem(f.code(),f.registration())));
        assertEquals(1,Collections.frequency(outcomes,true));
        try(var c=owner(db)) {
            assertEquals(1,count(c,"SELECT COUNT(*) FROM dbo.AiDelegatedSession"));assertEquals(2,count(c,"SELECT COUNT(*) FROM dbo.AiOAuthCredential"));
            assertEquals(300,count(c,"SELECT DATEDIFF(second,IssuedAt,ExpiresAt) FROM dbo.AiOAuthCredential WHERE Kind='ACCESS'"));
            assertEquals(0,count(c,"SELECT COUNT(*) FROM dbo.AiDelegatedSession WHERE ExpiresAt>DATEADD(hour,24,ConsentAt) OR IdleExpiresAt>DATEADD(hour,8,IssuedAt)"));
            assertEquals(0,count(c,"SELECT COUNT(*) FROM dbo.AiOAuthCredential c JOIN dbo.AiDelegatedSession s ON s.SessionId=c.SessionId WHERE c.ShaleClientId<>s.ShaleClientId OR c.UserId<>s.UserId OR c.IntegrationId<>s.IntegrationId OR c.GrantId<>s.GrantId OR c.ScopeMask<>s.ScopeMask"));
        }
    }
    @Test void credentialRotationResponseLossRetryRevokesAllDescendantsPermanently()throws Exception {
        var f=credentialFixture();var first=redeem(f.code(),f.registration());
        assertEquals(32,AiOAuthCredentialStore.digest(first.accessToken()).length);
        var second=refresh(first.refreshToken(),f.registration());var third=refresh(second.refreshToken(),f.registration());
        assertNotEquals(first.accessToken(),second.accessToken());assertNotEquals(first.refreshToken(),second.refreshToken());
        assertThrows(SecurityException.class,()->refresh(first.refreshToken(),f.registration()),"Lost response retry intentionally revokes the family");
        assertThrows(RuntimeException.class,()->refresh(third.refreshToken(),f.registration()));
        try(var c=owner(db)) {
            assertEquals(6,count(c,"SELECT COUNT(*) FROM dbo.AiOAuthCredential WHERE RevokedAt IS NOT NULL"));
            assertEquals(1,count(c,"SELECT COUNT(*) FROM dbo.AiDelegatedSession WHERE RevocationReason='REUSE'"));
            assertEquals(2,count(c,"SELECT COUNT(*) FROM dbo.AiOAuthCredential WHERE ConsumedAt IS NOT NULL"));
            String original=scalar(c,"SELECT CONVERT(varchar(33),RevokedAt,126) FROM dbo.AiDelegatedSession");
            assertThrows(SecurityException.class,()->refresh(first.refreshToken(),f.registration()));
            assertEquals(original,scalar(c,"SELECT CONVERT(varchar(33),RevokedAt,126) FROM dbo.AiDelegatedSession"));
        }
        replacementCredentialProbe(third.refreshToken(),f.registration(),false);
    }
    @Test void credentialConcurrentRefreshHasOneWinnerAndLoserRevokesFamily()throws Exception {
        var f=credentialFixture();var first=redeem(f.code(),f.registration());
        var outcomes=race(()->credentialAttempt(()->refresh(first.refreshToken(),f.registration())),()->credentialAttempt(()->refresh(first.refreshToken(),f.registration())));
        assertEquals(1,Collections.frequency(outcomes,true));
        try(var c=owner(db)) {assertEquals(4,count(c,"SELECT COUNT(*) FROM dbo.AiOAuthCredential"));assertEquals(4,count(c,"SELECT COUNT(*) FROM dbo.AiOAuthCredential WHERE RevokedAt IS NOT NULL"));}
    }
    private boolean credentialAttempt(ThrowingRunnable task)throws Exception {try{task.run();return true;}catch(SecurityException|IllegalStateException denied){return false;}}
    @Test void credentialReuseRaceWithDescendantActivityLeavesNoActiveCredentials()throws Exception {
        var f=credentialFixture();var first=redeem(f.code(),f.registration());var second=refresh(first.refreshToken(),f.registration());
        race(()->credentialAttempt(()->refresh(first.refreshToken(),f.registration())),()->credentialAttempt(()->refresh(second.refreshToken(),f.registration())));
        try(var c=owner(db)) {assertEquals(0,count(c,"SELECT COUNT(*) FROM dbo.AiOAuthCredential WHERE RevokedAt IS NULL"));assertEquals(1,count(c,"SELECT COUNT(*) FROM dbo.AiDelegatedSession WHERE RevocationReason='REUSE'"));}
    }
    @Test void credentialWrongClientAndAccessTokenCannotRefreshOrResolve()throws Exception {
        var f=credentialFixture();var first=redeem(f.code(),f.registration());var other=registration(false);
        assertThrows(RuntimeException.class,()->refresh(first.refreshToken(),other));
        assertThrows(RuntimeException.class,()->refresh(first.accessToken(),f.registration()));
        var boot=new AiOAuthCredentialStore(provider("bootstrap",null,null),ISSUER,RESOURCE);
        assertTrue(boot.resolveRefresh(first.accessToken(),f.registration().clientId()).isEmpty());assertTrue(boot.resolveRefresh(first.refreshToken(),other.clientId()).isEmpty());
        assertEquals(new AiOAuthExchangeStore.Owner(7,72),boot.resolveRefresh(first.refreshToken(),f.registration().clientId()).orElseThrow());
        assertNotNull(refresh(first.refreshToken(),f.registration()));
    }
    @org.junit.jupiter.params.ParameterizedTest(name="Refresh authority denies {0}")
    @org.junit.jupiter.params.provider.ValueSource(strings={"global","tenant","user","removed","tenant-active","integration","grant","session","web","scope"})
    void credentialCommittedRevocationDeniesAndReenableCannotRestore(String change)throws Exception {
        var f=credentialFixture();var first=redeem(f.code(),f.registration());
        String mutation=switch(change) {
            case "global"->"UPDATE dbo.AiProductCapability SET Enabled=0,Revision=Revision+1";
            case "tenant"->"UPDATE dbo.TenantAiIntegrationPolicy SET Enabled=0";
            case "user"->"UPDATE dbo.Users SET is_deleted=1 WHERE id=72";
            case "removed"->"UPDATE dbo.Users SET IsRemoved=1 WHERE id=72";
            case "tenant-active"->"UPDATE dbo.ShaleClients SET IsActive=0 WHERE Id=7";
            case "integration"->"UPDATE dbo.AiIntegrationRegistration SET Status='SUSPENDED'";
            case "grant"->"UPDATE dbo.AiDelegationGrant SET RevokedAt=SYSUTCDATETIME(),RevokedByUserId=71,RevocationReason='ADMIN'";
            case "session"->"UPDATE dbo.AiDelegatedSession SET RevokedAt=SYSUTCDATETIME(),RevocationReason='ADMIN'";
            case "web"->"UPDATE dbo.UserSessions SET RevokedAt=SYSUTCDATETIME()";
            case "scope"->"UPDATE dbo.AiIntegrationRegistration SET ScopeMask=0";
            default->throw new AssertionError();
        };
        if(change.equals("tenant")) service(7,71).setTenantEnabled(false,tenantVersion());
        else if(change.equals("scope")) service(7,71).changeIntegration(f.registration().integrationId(),AiScope.fromMask(0),false,f.registration().rowVersion());
        else try(var c=owner(db)){sql(c,mutation);}
        assertThrows(RuntimeException.class,()->refresh(first.refreshToken(),f.registration()));
        if(Set.of("global","user","removed","tenant-active").contains(change)) {
            try(var c=owner(db)){sql(c,mutation.replace("Enabled=0","Enabled=1").replace("is_deleted=1","is_deleted=0").replace("IsRemoved=1","IsRemoved=0").replace("IsActive=0","IsActive=1"));}
            assertThrows(RuntimeException.class,()->refresh(first.refreshToken(),f.registration()));
        }
    }
    @org.junit.jupiter.params.ParameterizedTest(name="Credential deadline {0}")
    @org.junit.jupiter.params.provider.ValueSource(strings={"access-clip","refresh-expired","idle","absolute","registration","grant"})
    void credentialDeadlineClippingAndExpiredAuthorityFailClosed(String change)throws Exception {
        var f=credentialFixture();
        if(change.equals("access-clip")) {
            try(var c=owner(db)){sql(c,"UPDATE dbo.UserSessions SET ExpiresAt=DATEADD(second,30,SYSUTCDATETIME())");}
            var first=redeem(f.code(),f.registration());assertTrue(java.time.Duration.between(first.issuedAt(),first.accessExpiresAt()).toSeconds()<=30);return;
        }
        var first=redeem(f.code(),f.registration());
        try(var c=owner(db)) {
            // Explicit synthetic owner-only timestamp fault injection, never production evidence.
            if(change.equals("refresh-expired")) {
                sql(c,"DISABLE TRIGGER dbo.AiImmutable_AiOAuthCredential ON dbo.AiOAuthCredential");
                sql(c,"UPDATE dbo.AiOAuthCredential SET IssuedAt=DATEADD(hour,-2,SYSUTCDATETIME()),ExpiresAt=DATEADD(hour,-1,SYSUTCDATETIME()) WHERE Kind='REFRESH'");
            } else if(change.equals("idle")||change.equals("absolute")) {
                sql(c,"DISABLE TRIGGER dbo.AiImmutable_AiDelegatedSession ON dbo.AiDelegatedSession");
                sql(c,"UPDATE dbo.AiDelegatedSession SET IssuedAt=DATEADD(hour,-2,SYSUTCDATETIME()),IdleExpiresAt=DATEADD(hour,-1,SYSUTCDATETIME())"+(change.equals("absolute")?",ExpiresAt=DATEADD(minute,-30,SYSUTCDATETIME())":""));
            } else {
                String table=change.equals("registration")?"AiIntegrationRegistration":"AiDelegationGrant";
                sql(c,"DISABLE TRIGGER dbo.AiImmutable_"+table+" ON dbo."+table);
                sql(c,"UPDATE dbo."+table+" SET "+(change.equals("grant")?"ConsentedAt":"CreatedAt")+"=DATEADD(hour,-2,SYSUTCDATETIME()),ExpiresAt=DATEADD(hour,-1,SYSUTCDATETIME())");
            }
        }
        assertThrows(RuntimeException.class,()->refresh(first.refreshToken(),f.registration()));
    }
    @Test void credentialInitialAndRotationAuditFailureRollBackAllState()throws Exception {
        var f=credentialFixture();
        try(var c=owner(db)){sql(c,"CREATE TRIGGER dbo.FailFamilyAudit ON dbo.EntityActionAuditLog AFTER INSERT AS IF EXISTS(SELECT 1 FROM inserted WHERE EntityType='AI_DELEGATED_SESSION') THROW 58999,'Injected family audit failure',1");}
        assertThrows(RuntimeException.class,()->redeem(f.code(),f.registration()));
        try(var c=owner(db)){assertEquals(0,count(c,"SELECT COUNT(*) FROM dbo.AiDelegatedSession"));assertEquals(0,count(c,"SELECT COUNT(*) FROM dbo.AiAuthorizationTransaction WHERE ConsumedAt IS NOT NULL"));sql(c,"DROP TRIGGER dbo.FailFamilyAudit");}
        var first=redeem(f.code(),f.registration());
        try(var c=owner(db)){sql(c,"CREATE TRIGGER dbo.FailFamilyAudit ON dbo.EntityActionAuditLog AFTER INSERT AS THROW 58999,'Injected rotation audit failure',1");}
        assertThrows(RuntimeException.class,()->refresh(first.refreshToken(),f.registration()));
        try(var c=owner(db)){assertEquals(2,count(c,"SELECT COUNT(*) FROM dbo.AiOAuthCredential"));assertEquals(0,count(c,"SELECT COUNT(*) FROM dbo.AiOAuthCredential WHERE ConsumedAt IS NOT NULL"));sql(c,"DROP TRIGGER dbo.FailFamilyAudit");}
        assertNotNull(refresh(first.refreshToken(),f.registration()));
    }
    @Test void credentialRestartObservesAccessRefreshAndConsumedHistory()throws Exception {
        var f=credentialFixture();var first=redeem(f.code(),f.registration());
        try(var c=owner(db);var p=c.prepareStatement("SELECT COUNT(*) FROM dbo.AiOAuthCredential WHERE CredentialHash=? AND Kind='ACCESS'")) {
            p.setBytes(1,AiOAuthCredentialStore.digest(first.accessToken()));try(var rows=p.executeQuery()){rows.next();assertEquals(1,rows.getInt(1));}
        }
        replacementCredentialProbe(first.refreshToken(),f.registration(),true);
        assertThrows(SecurityException.class,()->refresh(first.refreshToken(),f.registration()));
        replacementCredentialProbe(first.refreshToken(),f.registration(),false);
    }
    private void replacementCredentialProbe(String token,Registration r,boolean success)throws Exception {
        ProcessBuilder child=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin","java").toString(),"-cp",System.getProperty("surefire.test.class.path"),CredentialRestartProbe.class.getName());
        var env=child.environment();env.put("AI_PROBE_URL",url(db));env.put("AI_PROBE_USER",prefix+"_runtime");env.put("AI_PROBE_PASSWORD",logins.get("runtime"));
        env.put("AI_PROBE_REFRESH",token);env.put("AI_PROBE_CLIENT",r.clientId());env.put("AI_PROBE_SUCCESS",Boolean.toString(success));
        var process=child.redirectErrorStream(true).start();
        try {assertTrue(process.waitFor(20,TimeUnit.SECONDS));assertEquals(0,process.exitValue(),"Replacement JVM must observe durable family state");}
        finally {if(process.isAlive())process.destroyForcibly();}
    }
    public static class CredentialRestartProbe {
        public static void main(String[] args)throws Exception {
            var store=new AiOAuthCredentialStore(()->{
                try {var c=DriverManager.getConnection(System.getenv("AI_PROBE_URL"),System.getenv("AI_PROBE_USER"),System.getenv("AI_PROBE_PASSWORD"));
                    sql(c,"EXEC sys.sp_set_session_context @key=N'ShaleClientId',@value=7; EXEC sys.sp_set_session_context @key=N'PrincipalUserId',@value=72");return c;
                }catch(SQLException e){throw new IllegalStateException(e);}
            },ISSUER,RESOURCE);
            boolean expected=Boolean.parseBoolean(System.getenv("AI_PROBE_SUCCESS"));
            try {store.refresh(System.getenv("AI_PROBE_REFRESH"),System.getenv("AI_PROBE_CLIENT"),null,null,null,null);if(!expected)throw new AssertionError("Revoked family accepted");}
            catch(SecurityException|IllegalStateException denied){if(expected)throw denied;}
        }
    }

    @Test void credentialConfidentialRefreshRequiresSameKeyFreshSharedJtiAndLiveKey()throws Exception {
        UUID web=installOAuth();installCredentials();enabled();var r=registration(true);var admin=service(7,71);
        var key=admin.rotateClientKey(r.integrationId(),publicKey(),30,r.rowVersion());String code=opaque();issueOAuth(startOAuth(r,web,opaque()),web,code);
        var now=java.time.Instant.now().minusSeconds(1);
        var first=credentials().redeem(code,r.clientId(),CALLBACK,VERIFIER,key.credentialId(),"initial-jti",now,now.plusSeconds(60));
        assertThrows(RuntimeException.class,()->credentials().refresh(first.refreshToken(),r.clientId(),null,null,null,null));
        assertThrows(RuntimeException.class,()->credentials().refresh(first.refreshToken(),r.clientId(),key.credentialId(),"initial-jti",now,now.plusSeconds(60)));
        assertThrows(RuntimeException.class,()->credentials().refresh(first.refreshToken(),r.clientId(),UUID.randomUUID(),"wrong-key-jti",now,now.plusSeconds(60)));
        var second=credentials().refresh(first.refreshToken(),r.clientId(),key.credentialId(),"rotation-jti",now,now.plusSeconds(60));
        admin.revokeClientKey(r.integrationId(),key.credentialId(),key.rowVersion());
        assertThrows(RuntimeException.class,()->credentials().refresh(second.refreshToken(),r.clientId(),key.credentialId(),"revoked-key-jti",now,now.plusSeconds(60)));
        try(var c=owner(db)){assertEquals(4,count(c,"SELECT COUNT(*) FROM dbo.AiOAuthCredential WHERE RevokedAt IS NOT NULL"));}
    }
    @Test void credentialCommittedRevokeBeforeRotationPreventsNewRows()throws Exception {
        var f=credentialFixture();var first=redeem(f.code(),f.registration());
        try(var c=owner(db)){sql(c,"UPDATE dbo.AiDelegatedSession SET RevokedAt=SYSUTCDATETIME(),RevocationReason='ADMIN'");}
        assertThrows(RuntimeException.class,()->refresh(first.refreshToken(),f.registration()));
        try(var c=owner(db)){assertEquals(2,count(c,"SELECT COUNT(*) FROM dbo.AiOAuthCredential"));}
    }
    @Test void credentialSecurityResetWebRevocationAuditsFamilyAndCannotResurrect()throws Exception {
        var f=credentialFixture();var first=redeem(f.code(),f.registration());
        try(var c=owner(db)) {
            // Same durable transition used by UserDao.revokeAllSessions for administrative reset.
            sql(c,"UPDATE dbo.UserSessions SET RevokedAt=SYSUTCDATETIME() WHERE UserId=72 AND RevokedAt IS NULL");
            assertEquals(1,count(c,"SELECT COUNT(*) FROM dbo.AiDelegatedSession WHERE RevocationReason='SECURITY'"));
            assertEquals(2,count(c,"SELECT COUNT(*) FROM dbo.AiOAuthCredential WHERE RevokedAt IS NOT NULL"));
            assertEquals(1,count(c,"SELECT COUNT(*) FROM dbo.EntityActionAuditLog WHERE EntityType='AI_DELEGATED_SESSION' AND Action='DEACTIVATED'"));
        }
        assertThrows(RuntimeException.class,()->refresh(first.refreshToken(),f.registration()));
    }
    @Test void credentialReuseAuditFailureRollsBackRevocationAndRetriesFailClosed()throws Exception {
        var f=credentialFixture();var first=redeem(f.code(),f.registration());var second=refresh(first.refreshToken(),f.registration());
        try(var c=owner(db)){sql(c,"CREATE TRIGGER dbo.FailReuseAudit ON dbo.EntityActionAuditLog AFTER INSERT AS THROW 58999,'Injected reuse audit failure',1");}
        assertThrows(IllegalStateException.class,()->refresh(first.refreshToken(),f.registration()));
        try(var c=owner(db)){assertEquals(0,count(c,"SELECT COUNT(*) FROM dbo.AiDelegatedSession WHERE RevokedAt IS NOT NULL"));sql(c,"DROP TRIGGER dbo.FailReuseAudit");}
        assertThrows(SecurityException.class,()->refresh(first.refreshToken(),f.registration()));
        assertThrows(RuntimeException.class,()->refresh(second.refreshToken(),f.registration()));
    }

}
