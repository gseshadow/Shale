package com.shale.data.security.ai;

import com.shale.core.security.ai.AiScope;
import org.junit.jupiter.api.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.sql.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Select only dcr* methods. Real restricted JDBC and unchanged authoritative fixture; no substitute schema. */
class AiDcrApplicationLocalSqlServerTest extends AiCimdAuthorizationLocalSqlServerTest {
    static final String FIXTURE_SHA="4b64b50da531ebc6a2eb9dc26c6555ef555621df371fe9d8a5cf994fb6e8417f";
    static final Map<String,String> ROLES=Map.ofEntries(
            Map.entry("v2registrar","AiDcrRegistrarV2"),Map.entry("v2admin","AiDcrAdministratorV2"),Map.entry("v2stored","AiDcrMetadataReaderV2"),
            Map.entry("v2case","AiExternalCaseRuntimeV2"),Map.entry("v2life","AiExternalLifecycleV2"),Map.entry("v2metadata","AiExternalMetadataPublisherV2"),
            Map.entry("v2browser","AiExternalOAuthBrowserV2"),Map.entry("v2issuer","AiExternalOAuthIssuerV2"),Map.entry("v2resolver","AiExternalOAuthResolverV2"),
            Map.entry("v2neutral","AiExternalOAuthNeutralV2"),Map.entry("v2onboarding","AiExternalOnboardingRuntimeV2"));
    private boolean authoritativeVerified;
    @Override Connection owner(String database)throws SQLException {
        // Keep the ancestor's earliest opt-in gate, and enforce this exact fixture
        // before even its first nonce login is created.
        if(!authoritativeVerified)try {
            assertEquals(FIXTURE_SHA,HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(Path.of(System.getenv("SHALE_CASE_VERIFY_SCHEMA"))))),"Never substitute the authoritative fixture");
            assertEquals(FIXTURE_SHA,System.getenv("SHALE_CASE_VERIFY_SCHEMA_SHA256"));authoritativeVerified=true;
        }catch(java.io.IOException|java.security.NoSuchAlgorithmException invalid){throw new SQLException("Authoritative fixture verification required.",invalid);}
        return super.owner(database);
    }
    @BeforeAll void externalIdentities()throws Exception {
        try(var c=owner("master")){for(String role:ROLES.keySet()){
            String secret=UUID.randomUUID()+"aB9!";sql(c,"CREATE LOGIN ["+prefix+"_"+role+"] WITH PASSWORD='"+secret+"',CHECK_POLICY=OFF");secrets.put(role,secret);
        }}
    }
    @BeforeEach void externalSuccessor()throws Exception {
        try(var c=owner(db)) {
            sql(c,"UPDATE dbo.AiProductCapability SET Enabled=0; UPDATE dbo.AiCimdProofControlV1 SET Enabled=0; UPDATE dbo.AiCimdOAuthControlV1 SET Enabled=0");
            installSql(c,Files.readString(Path.of("../docs/sql/ai-integration/universal-pr-b-onboarding.sql")));
            installSql(c,Files.readString(Path.of("../docs/sql/ai-integration/universal-pr-c1-external-v2.sql")));
            assertEquals(0,count(c,"SELECT CONVERT(int,Enabled) FROM dbo.AiExternalControlV2"));
            for(var role:ROLES.entrySet())sql(c,"ALTER ROLE "+role.getValue()+" ADD MEMBER ["+prefix+"_"+role.getKey()+"]");
            for(var entry:historical.entrySet())assertEquals(entry.getValue(),receipts(c).get(entry.getKey()));
            assertEquals(immutableBinding,binding(c));
            assertThrows(SQLException.class,()->installSql(c,Files.readString(Path.of("../docs/sql/ai-integration/universal-pr3-confinement-verify.sql"))),"V1 must reject the reviewed source delta, not silently accept it");
            sql(c,"UPDATE dbo.AiProductCapability SET Enabled=1; UPDATE dbo.AiCimdProofControlV1 SET Enabled=1; UPDATE dbo.AiCimdOAuthControlV1 SET Enabled=1; UPDATE dbo.AiExternalControlV2 SET Enabled=1");
        }
        externalAttestation().verify();
    }
    AiCaseDataAttestation externalAttestation(){return new AiCaseDataAttestation(source("ai"),source("catalog"),List.of(source("runtime"),source("bootstrap")),db,ISSUER,RESOURCE,false,contract(),AiExternalContract.EXTERNAL_V2);}
    AiDcrApplicationStore dcr(String role){return new AiDcrApplicationStore(()->{try{return connection(role,null,null);}catch(SQLException x){throw new IllegalStateException(x);}},ISSUER,RESOURCE);}
    AiCimdOAuthStore external(String role,Integer tenant,Integer user){return new AiCimdOAuthStore(()->{try{return connection(role,tenant,user);}catch(SQLException x){throw new IllegalStateException(x);}},ISSUER,RESOURCE,AiExternalContract.EXTERNAL_V2);}
    AiDcrApplicationStore.Registration register(){return dcr("v2registrar").register("Synthetic DCR","[\""+CALLBACK+"\"]","[]",1,3);}
    AiTenantApplicationService.Approval approve(AiDcrApplicationStore.Registration registration,int tenant,int user,int admin){
        var pending=new AiTenantApplicationService(provider("tenant",tenant,user)).request(registration.application().applicationId(),registration.application().rowVersion(),AiScope.fromMask(3));
        return new AiTenantApplicationService(provider("tenant",tenant,admin)).decide(pending.approvalId(),pending.rowVersion(),AiTenantApplicationService.Decision.APPROVE,AiScope.fromMask(3),1);
    }
    Flow dcrConsent(String client,int tenant,int user)throws Exception {
        UUID tx=UUID.randomUUID(),web=UUID.randomUUID();String nonce=AiOAuthCredentialStore.opaque(),code=AiOAuthCredentialStore.opaque();
        try(var c=owner(db);var p=c.prepareStatement("INSERT dbo.UserSessions(SessionId,ShaleClientId,UserId,ClientType,CurrentAccessJti,IssuedAt,ExpiresAt) VALUES(?,?,?,'WEB',NEWID(),SYSUTCDATETIME(),DATEADD(hour,2,SYSUTCDATETIME()))")){
            p.setObject(1,web);p.setInt(2,tenant);p.setInt(3,user);p.executeUpdate();
        }
        dcr("v2stored").prepare(new DcrClientIdentifier(client));var browser=external("v2browser",tenant,user);
        assertTrue(browser.loadClient(client,web).isPresent());
        browser.begin(tx,web,client,CALLBACK,AiOAuthExchangeStore.challenge(VERIFIER),"synthetic state",AiScope.fromMask(3),nonce);
        assertTrue(external("v2resolver",null,null).resolve(code,client,"CODE").isEmpty(),"Approval/intent must never issue a code before explicit consent");
        assertEquals(client,browser.findIntent(nonce,web).orElseThrow().clientId());
        browser.approve(tx,web,AiScope.fromMask(3));browser.complete(tx,web,AiScope.fromMask(3),code);
        return new Flow(tenant,user,tx,web,nonce,code);
    }
    AiOAuthCredentialStore.Tokens dcrRedeem(String client,Flow flow){
        var owner=external("v2resolver",null,null).resolve(flow.code(),client,"CODE").orElseThrow();
        assertEquals(flow.tenant(),owner.tenantId());assertEquals(flow.user(),owner.userId());
        var issuer=external("v2issuer",flow.tenant(),flow.user());assertEquals("none",issuer.clientProof(flow.code(),client,"CODE").method());
        return issuer.redeem(flow.code(),client,CALLBACK,VERIFIER,null,null,null,null);
    }
    AiCimdCaseData externalCases(){return new AiCimdCaseData(deadline->externalAttestation(),deadline->source("v2case"),deadline->source("cimdreview"),db,ISSUER,RESOURCE,AiExternalContract.EXTERNAL_V2);}

    @Test void dcrNeutralCreationTwoTenantConsentAndCredentialSeparation()throws Exception {
        int before;
        try(var c=owner(db)){before=count(c,"SELECT (SELECT COUNT(*) FROM dbo.AiTenantApplicationApproval)+(SELECT COUNT(*) FROM dbo.AiDelegationGrant)+(SELECT COUNT(*) FROM dbo.AiDelegatedSession)+(SELECT COUNT(*) FROM dbo.AiCimdCredentialV1)");sql(c,"UPDATE dbo.AiProductCapability SET Enabled=0");}
        var registration=register();String client=registration.identifier().value();
        try(var c=owner(db)){
            assertEquals(before,count(c,"SELECT (SELECT COUNT(*) FROM dbo.AiTenantApplicationApproval)+(SELECT COUNT(*) FROM dbo.AiDelegationGrant)+(SELECT COUNT(*) FROM dbo.AiDelegatedSession)+(SELECT COUNT(*) FROM dbo.AiCimdCredentialV1)"),"Tenant-neutral registration creates no authority");
            sql(c,"UPDATE dbo.AiProductCapability SET Enabled=1");
        }
        assertThrows(IllegalArgumentException.class,()->new AiApplicationRegistryService(()->{throw new AssertionError("DCR cannot reach CIMD publication SQL");},ISSUER,RESOURCE).publishCimd(client,new byte[32],1,false,null));
        var a=approve(registration,7,72,71);var b=approve(registration,8,82,81);assertNotEquals(a.integrationId(),b.integrationId());
        var fa=dcrConsent(client,7,72);var fb=dcrConsent(client,8,82);var ta=dcrRedeem(client,fa);var tb=dcrRedeem(client,fb);
        try(var cases=externalCases()){
            disclosure(cases,ta.accessToken(),7,72,100);disclosure(cases,tb.accessToken(),8,82,200);
            assertThrows(RuntimeException.class,()->disclosure(cases,ta.accessToken(),7,72,200));
        }
        assertThrows(RuntimeException.class,()->cimd(ta.accessToken()),"CIMD-only Case loader must reject DCR credentials");
        assertThrows(RuntimeException.class,()->authenticate(ta.accessToken()),"Manual Case loader must reject DCR credentials");
        try(var c=connection("cimdresolver",null,null)){assertTrue(AiSql.call(c,"AiCimdResolveCodeV1",r->true,AiOAuthCredentialStore.digest(fa.code()),client,ISSUER,RESOURCE).isEmpty());}
        read(7,72,redeem(consent(7,72)).accessToken(),100);manualRead();
    }
    @Test void dcrTenantRevocationAndApplicationRevisionInvalidateOnlyTheirAuthority()throws Exception {
        var registration=register();String client=registration.identifier().value();var a=approve(registration,7,72,71);approve(registration,8,82,81);
        var ta=dcrRedeem(client,dcrConsent(client,7,72));var tb=dcrRedeem(client,dcrConsent(client,8,82));
        var rotated=external("v2issuer",8,82).refresh(tb.refreshToken(),client,null,null,null,null);
        new AiTenantApplicationService(provider("tenant",7,71)).decide(a.approvalId(),a.rowVersion(),AiTenantApplicationService.Decision.REVOKE,Set.of(),0);
        try(var cases=externalCases()){
            assertThrows(RuntimeException.class,()->disclosure(cases,ta.accessToken(),7,72,100));disclosure(cases,rotated.accessToken(),8,82,200);
        }
        assertThrows(RuntimeException.class,()->external("v2issuer",7,72).refresh(ta.refreshToken(),client,null,null,null,null));
        dcr("v2admin").changeState(registration.application().applicationId(),true,registration.application().rowVersion());
        assertThrows(RuntimeException.class,()->dcr("v2stored").prepare(registration.identifier()));
        assertThrows(RuntimeException.class,()->external("v2issuer",8,82).refresh(rotated.refreshToken(),client,null,null,null,null));
        try(var cases=externalCases()){assertThrows(RuntimeException.class,()->disclosure(cases,rotated.accessToken(),8,82,200));}
        manualRead();
    }
    @Test void dcrSuccessorRejectsDriftWithoutChangingHistoricalReceipts()throws Exception {
        externalAttestation().verify();
        try(var c=owner(db)){
            sql(c,"ALTER TABLE dbo.AiDcrMetadataV2 ADD Unexpected int NULL");
            assertThrows(IllegalStateException.class,()->externalAttestation().verify());
            sql(c,"ALTER TABLE dbo.AiDcrMetadataV2 DROP COLUMN Unexpected");
            for(var entry:historical.entrySet())assertEquals(entry.getValue(),receipts(c).get(entry.getKey()));assertEquals(immutableBinding,binding(c));
        }
        // DROP COLUMN may leave catalog column IDs unchanged for existing columns; verify exact recovery.
        externalAttestation().verify();
        assertThrows(IllegalStateException.class,()->attestation().verify(),"Default predecessor selection cannot suppress V2 drift/failure");
    }
    @Test void dcrRegistrationAuditFailureRollsBackAllApplicationRecords()throws Exception {
        int before;
        try(var c=owner(db)){
            before=count(c,"SELECT COUNT(*) FROM dbo.AiExternalApplication WHERE Source='DCR'");
            sql(c,"EXEC(N'CREATE TRIGGER dbo.C1FailGlobalAudit ON dbo.GlobalControlPlaneAuditLog AFTER INSERT AS THROW 59690,''Synthetic audit failure'',1;')");
        }
        assertThrows(IllegalStateException.class,this::register);
        try(var c=owner(db)){
            assertEquals(before,count(c,"SELECT COUNT(*) FROM dbo.AiExternalApplication WHERE Source='DCR'"));
            assertEquals(0,count(c,"SELECT COUNT(*) FROM dbo.AiDcrMetadataV2"));sql(c,"DROP TRIGGER dbo.C1FailGlobalAudit");
        }
        externalAttestation().verify();
    }
}
