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

    private void seedReservations(Registration r,int user,UUID sid,int amount,int rowCost,int secondsAgo)throws Exception {
        try(Connection c=owner(db);PreparedStatement p=c.prepareStatement("INSERT dbo.AiQuotaReservation(ShaleClientId,IntegrationId,UserId,GrantId,SessionId,InvocationId,Operation,RowCost,Admitted,AdmittedAt,LeaseExpiresAt,ReleasedAt) SELECT ShaleClientId,IntegrationId,UserId,GrantId,SessionId,NEWID(),?,?,1,DATEADD(second,-?,SYSUTCDATETIME()),DATEADD(second,-?,SYSUTCDATETIME()),SYSUTCDATETIME() FROM dbo.AiDelegatedSession WHERE SessionId=?")) {
            p.setString(1,rowCost==10?"SEARCH":"OVERVIEW");p.setInt(2,rowCost);p.setInt(3,secondsAgo);p.setInt(4,secondsAgo);p.setObject(5,sid);
            for(int i=0;i<amount;i++)assertEquals(1,p.executeUpdate());
        }
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
}
