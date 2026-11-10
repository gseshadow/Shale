package com.shale.data.security.ai;

import com.shale.core.security.ai.AiScope;
import org.junit.jupiter.api.*;
import java.nio.file.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Only explicitly selected lifecycle methods run. Actual restricted JDBC on the hash-gated private fixture. */
class AiCimdOAuthLocalSqlServerTest extends AiCimdProofLocalSqlServerTest {
    record Flow(int tenant,int user,UUID transaction,UUID web,String nonce,String code) {
        @Override public String toString(){return "SyntheticCimdFlow[redacted]";}
    }
    @BeforeAll void oauthIdentities()throws Exception {
        try(Connection c=owner("master")){for(String role:List.of("cimdbrowser","cimdissuer","cimdresolver","cimdmetadata","cimdneutral")){
            String secret=UUID.randomUUID()+"aB9!";sql(c,"CREATE LOGIN ["+prefix+"_"+role+"] WITH PASSWORD='"+secret+"',CHECK_POLICY=OFF");secrets.put(role,secret);
        }}
    }
    @Override String url(String database){return super.url(database).replace("socketTimeout=10000","socketTimeout=120000");}
    /** Only fixture-owner DDL gets a larger compilation deadline; restricted business calls retain five seconds. */
    @Override Connection owner(String database)throws SQLException {
        var source=new com.microsoft.sqlserver.jdbc.SQLServerDataSource();source.setURL(url(database));source.setUser("sa");source.setPassword(password);source.setSocketTimeout(120000);
        Connection original=source.getConnection();
        original.setNetworkTimeout(Runnable::run,120000);
        return (Connection)java.lang.reflect.Proxy.newProxyInstance(Connection.class.getClassLoader(),new Class[]{Connection.class},(p,m,args)->{
            Object value=invoke(original,m,args);
            if(m.getName().equals("createStatement")) {
                Statement statement=(Statement)value;
                return java.lang.reflect.Proxy.newProxyInstance(Statement.class.getClassLoader(),new Class[]{Statement.class},(sp,sm,sa)->{
                    if(sm.getName().equals("setQueryTimeout")){sa=new Object[]{120};}
                    return invoke(statement,sm,sa);
                });
            }
            return value;
        });
    }
    static void installSql(Connection c,String text)throws SQLException {try(var statement=c.createStatement()){statement.setQueryTimeout(120);boolean result=statement.execute(text);while(result||statement.getUpdateCount()!=-1){if(result)try(var rows=statement.getResultSet()){while(rows.next()){} }result=statement.getMoreResults(Statement.CLOSE_CURRENT_RESULT);}}}
    @Override @BeforeEach void proofFoundation(TestInfo info)throws Exception {
        System.err.println("PR4B disposable scenario: "+contract()+" / "+info.getTestMethod().orElseThrow().getName());
        installCompatibility();
        try(Connection c=owner(db)) {
            historical=receipts(c);immutableBinding=binding(c);
            installSql(c,Files.readString(Path.of("../docs/sql/ai-integration/universal-pr4a-proof.sql")));
            sql(c,"ALTER ROLE AiCimdCaseRuntimeV1 ADD MEMBER ["+prefix+"_cimd]; ALTER ROLE AiCimdLifecycleV1 ADD MEMBER ["+prefix+"_cimdlife]");
            installSql(c,Files.readString(Path.of("../docs/sql/ai-integration/universal-pr4a-proof-verify.sql")));
            sql(c,"UPDATE dbo.AiProductCapability SET Enabled=1 WHERE Id=1; UPDATE dbo.AiCimdProofControlV1 SET Enabled=1 WHERE Id=1");
        }
        application=publishApplication();approvalA=approve(7,72,71);approvalB=approve(8,82,81);oauthFoundation(info);
    }
    void oauthFoundation(TestInfo info)throws Exception {
        try(Connection c=owner(db)){
            sql(c,"UPDATE dbo.AiProductCapability SET Enabled=0 WHERE Id=1");
            String candidate=Files.readString(Path.of("../docs/sql/ai-integration/universal-pr4b-oauth.sql"));
            if(info.getTestMethod().orElseThrow().getName().equals("lifecycleInstallerLateFaultAndRuntimePrivilegesFailClosed")) {
                String injected=candidate.replace("/* C4b final independent seal verification */","GRANT EXECUTE ON dbo.AiCimdIssueV1 TO AiCimdOAuthIssuerV1;\n/* C4b final independent seal verification */");
                assertNotEquals(candidate,injected);assertThrows(SQLException.class,()->installSql(c,injected),"Late authority drift must roll back DDL and its receipt.");
                assertEquals(0,count(c,"SELECT COUNT(*) FROM sys.tables WHERE name IN('AiCimdPublicationV1','AiCimdKeyBindingV1','AiCimdOAuthControlV1','AiCimdOAuthReceiptV1','AiCimdAuthorizationV1','AiCimdAssertionReplayV1','AiCimdNeutralReplayV1')"));
                assertEquals(historical,receipts(c));assertEquals(immutableBinding,binding(c));
            }
            installSql(c,candidate);
            assertEquals(0,count(c,"SELECT CONVERT(int,Enabled) FROM dbo.AiCimdOAuthControlV1"));
            for(var entry:Map.of("cimdneutral","AiCimdOAuthNeutralV1","cimdbrowser","AiCimdOAuthBrowserV1","cimdissuer","AiCimdOAuthIssuerV1","cimdresolver","AiCimdOAuthResolverV1","cimdmetadata","AiCimdMetadataPublisherV1").entrySet())sql(c,"ALTER ROLE "+entry.getValue()+" ADD MEMBER ["+prefix+"_"+entry.getKey()+"]");
            installSql(c,Files.readString(Path.of("../docs/sql/ai-integration/universal-pr4b-oauth-verify.sql")));
            sql(c,"UPDATE dbo.AiProductCapability SET Enabled=1 WHERE Id=1; UPDATE dbo.AiCimdOAuthControlV1 SET Enabled=1 WHERE Id=1");
        }
        neutral("cimdmetadata").publishMetadata(application.applicationId(),1,new byte[32],"[\""+CALLBACK+"\"]","[]",Instant.now().plusSeconds(240));
    }
    AiCimdOAuthStore neutral(String role){return new AiCimdOAuthStore(()->{try{return connection(role,null,null);}catch(SQLException failure){throw new IllegalStateException(failure);}},ISSUER,RESOURCE);}
    AiCimdOAuthStore browser(int tenant,int user){return new AiCimdOAuthStore(provider("cimdbrowser",tenant,user),ISSUER,RESOURCE);}
    AiCimdOAuthStore issuer(int tenant,int user){return new AiCimdOAuthStore(provider("cimdissuer",tenant,user),ISSUER,RESOURCE);}
    Flow begin(int tenant,int user)throws Exception {
        UUID tx=UUID.randomUUID(),web=UUID.randomUUID();String nonce=AiOAuthCredentialStore.opaque()+"=",code=AiOAuthCredentialStore.opaque();
        try(Connection c=owner(db);var p=c.prepareStatement("INSERT dbo.UserSessions(SessionId,ShaleClientId,UserId,ClientType,CurrentAccessJti,IssuedAt,ExpiresAt) VALUES(?,?,?,'WEB',NEWID(),SYSUTCDATETIME(),DATEADD(hour,2,SYSUTCDATETIME()))")){
            p.setObject(1,web);p.setInt(2,tenant);p.setInt(3,user);p.executeUpdate();
        }
        var store=browser(tenant,user);assertTrue(store.loadClient(EXTERNAL,web).isPresent());
        store.begin(tx,web,EXTERNAL,CALLBACK,AiOAuthExchangeStore.challenge(VERIFIER),"exact client state",AiScope.fromMask(3),nonce);
        var intent=store.findIntent(nonce,web).orElseThrow();assertEquals(EXTERNAL,intent.clientId());assertEquals(tenant,intent.tenant());assertEquals(user,intent.user());
        return new Flow(tenant,user,tx,web,nonce,code);
    }
    Flow consent(int tenant,int user)throws Exception {
        var f=begin(tenant,user);var store=browser(tenant,user);
        assertTrue(neutral("cimdresolver").resolve(f.code(),EXTERNAL,"CODE").isEmpty(),"No code exists before explicit approval.");
        store.approve(f.transaction(),f.web(),AiScope.fromMask(3));store.complete(f.transaction(),f.web(),AiScope.fromMask(3),f.code());return f;
    }
    AiOAuthCredentialStore.Tokens redeem(Flow f){
        var owner=neutral("cimdresolver").resolve(f.code(),EXTERNAL,"CODE").orElseThrow();assertEquals(f.tenant(),owner.tenantId());assertEquals(f.user(),owner.userId());
        assertEquals("none",issuer(f.tenant(),f.user()).clientProof(f.code(),EXTERNAL,"CODE").method());
        return issuer(f.tenant(),f.user()).redeem(f.code(),EXTERNAL,CALLBACK,VERIFIER,null,null,null,null);
    }
    void read(int tenant,int user,String access,long caseId){ownRead(new Family(tenant,user,null,access,"",""),caseId);}
    @Test void lifecycleTwoTenantIssuanceRotationAndLegacyExclusion()throws Exception {
        var fa=consent(7,72);var fb=consent(8,82);var ta=redeem(fa);var tb=redeem(fb);
        read(7,72,ta.accessToken(),100);read(8,82,tb.accessToken(),200);manualRead();
        var rotated=issuer(7,72).refresh(ta.refreshToken(),EXTERNAL,null,null,null,null);read(7,72,rotated.accessToken(),100);
        assertThrows(RuntimeException.class,()->redeem(fa),"Code must remain single-use across fresh connections.");
        assertThrows(RuntimeException.class,()->authenticate(ta.accessToken()),"Legacy Case proof cannot authenticate CIMD credentials.");
        com.shale.core.runtime.DbSessionProvider legacyBootstrap=()->{try{return connection("bootstrap",null,null);}catch(SQLException failure){throw new IllegalStateException(failure);}};
        assertTrue(new AiOAuthExchangeStore(legacyBootstrap,ISSUER,RESOURCE).resolve(fa.code(),EXTERNAL).isEmpty());
        assertTrue(new AiOAuthCredentialStore(legacyBootstrap,ISSUER,RESOURCE).resolveRefresh(ta.refreshToken(),EXTERNAL).isEmpty());
        try(Connection c=owner(db)){assertEquals(0,count(c,"SELECT COUNT(*) FROM dbo.AiDelegatedSession s JOIN dbo.AiCimdFamilyV1 f ON f.ShaleClientId=s.ShaleClientId AND f.SessionId=s.SessionId WHERE s.TransactionId IS NOT NULL"));}
    }
    @Test void lifecycleRefreshReplayCommitsWholeFamilyRevocation()throws Exception {
        var ta=redeem(consent(7,72));var tb=redeem(consent(8,82));
        var rotated=issuer(7,72).refresh(ta.refreshToken(),EXTERNAL,null,null,null,null);
        assertThrows(SecurityException.class,()->issuer(7,72).refresh(ta.refreshToken(),EXTERNAL,null,null,null,null));
        assertThrows(RuntimeException.class,()->cimd(ta.accessToken()));assertThrows(RuntimeException.class,()->cimd(rotated.accessToken()));
        assertThrows(RuntimeException.class,()->issuer(7,72).refresh(rotated.refreshToken(),EXTERNAL,null,null,null,null));read(8,82,tb.accessToken(),200);manualRead();
    }
    @Test void lifecycleTokenRevocationIsTenantConfinedAndAudited()throws Exception {
        var ta=redeem(consent(7,72));var tb=redeem(consent(8,82));
        assertTrue(neutral("cimdresolver").resolve(ta.accessToken(),EXTERNAL,"REVOKE").isPresent());
        assertThrows(RuntimeException.class,()->issuer(8,82).revoke(ta.refreshToken(),EXTERNAL,null,null,null,null));read(7,72,ta.accessToken(),100);
        issuer(7,72).revoke(ta.accessToken(),EXTERNAL,null,null,null,null);assertThrows(RuntimeException.class,()->cimd(ta.accessToken()));
        assertThrows(RuntimeException.class,()->issuer(7,72).refresh(ta.refreshToken(),EXTERNAL,null,null,null,null));read(8,82,tb.accessToken(),200);manualRead();
    }
    @Test void lifecycleChangedPublicationBlocksPendingCodeExchangeAndRefresh()throws Exception {
        var pending=begin(7,72);var fa=consent(7,72);var fb=consent(8,82);var ta=redeem(fa);
        republish(false);
        assertThrows(RuntimeException.class,()->browser(7,72).approve(pending.transaction(),pending.web(),AiScope.fromMask(3)));
        assertThrows(RuntimeException.class,()->redeem(fb));
        assertThrows(RuntimeException.class,()->issuer(7,72).refresh(ta.refreshToken(),EXTERNAL,null,null,null,null));assertThrows(RuntimeException.class,()->cimd(ta.accessToken()));manualRead();
    }
    @Test void lifecycleApprovalRevocationBlocksCodeAndRefreshInOnlyItsTenant()throws Exception {
        var pending=consent(7,72);var ta=redeem(consent(7,72));var tb=redeem(consent(8,82));
        new AiTenantApplicationService(provider("tenant",7,71)).decide(approvalA.approvalId(),approvalA.rowVersion(),AiTenantApplicationService.Decision.REVOKE,Set.of(),0);
        assertThrows(RuntimeException.class,()->redeem(pending));
        assertThrows(RuntimeException.class,()->issuer(7,72).refresh(ta.refreshToken(),EXTERNAL,null,null,null,null));
        assertThrows(RuntimeException.class,()->cimd(ta.accessToken()));
        read(8,82,issuer(8,82).refresh(tb.refreshToken(),EXTERNAL,null,null,null,null).accessToken(),200);manualRead();
    }
    @Test void lifecycleWrongBindingsAndReplacementWebFailWithoutConsumingCode()throws Exception {
        var f=consent(7,72);var store=issuer(7,72);
        assertTrue(neutral("cimdresolver").resolve(f.code(),EXTERNAL.toUpperCase(Locale.ROOT),"CODE").isEmpty());
        assertThrows(RuntimeException.class,()->store.redeem(f.code(),EXTERNAL,CALLBACK,"x".repeat(43),null,null,null,null));
        assertThrows(RuntimeException.class,()->store.redeem(f.code(),EXTERNAL,CALLBACK+"/other",VERIFIER,null,null,null,null));
        assertThrows(RuntimeException.class,()->issuer(8,82).redeem(f.code(),EXTERNAL,CALLBACK,VERIFIER,null,null,null,null));
        assertThrows(RuntimeException.class,()->browser(7,72).findIntent(f.nonce(),UUID.randomUUID()));
        var t=redeem(f);read(7,72,t.accessToken(),100);
    }
    @Test void lifecycleAuditFailureRollsBackExchangeRotationReplayAndRevocation()throws Exception {
        var f=consent(7,72);var keep=redeem(consent(8,82));
        try(Connection c=owner(db)){sql(c,"CREATE TRIGGER dbo.Pr4bAuditFault ON dbo.EntityActionAuditLog AFTER INSERT AS THROW 59999,'Synthetic audit fault',1");}
        try{assertThrows(RuntimeException.class,()->redeem(f));}
        finally{try(Connection c=owner(db)){sql(c,"DROP TRIGGER dbo.Pr4bAuditFault");}}
        var ta=redeem(f);
        try(Connection c=owner(db)){sql(c,"CREATE TRIGGER dbo.Pr4bAuditFault ON dbo.EntityActionAuditLog AFTER INSERT AS THROW 59999,'Synthetic audit fault',1");}
        try{assertThrows(RuntimeException.class,()->issuer(7,72).refresh(ta.refreshToken(),EXTERNAL,null,null,null,null));assertThrows(RuntimeException.class,()->issuer(7,72).revoke(ta.accessToken(),EXTERNAL,null,null,null,null));}
        finally{try(Connection c=owner(db)){sql(c,"DROP TRIGGER dbo.Pr4bAuditFault");}}
        read(7,72,ta.accessToken(),100);var rotated=issuer(7,72).refresh(ta.refreshToken(),EXTERNAL,null,null,null,null);
        try(Connection c=owner(db)){sql(c,"CREATE TRIGGER dbo.Pr4bAuditFault ON dbo.EntityActionAuditLog AFTER INSERT AS THROW 59999,'Synthetic audit fault',1");}
        try{assertThrows(RuntimeException.class,()->issuer(7,72).refresh(ta.refreshToken(),EXTERNAL,null,null,null,null));}
        finally{try(Connection c=owner(db)){sql(c,"DROP TRIGGER dbo.Pr4bAuditFault");}}
        read(7,72,rotated.accessToken(),100);read(8,82,keep.accessToken(),200);manualRead();
    }
    @Test void lifecyclePrivateKeyBindingAndAssertionReplayAreAtomic()throws Exception {
        String external="https://private.test/client.json";
        byte[] digest=new byte[32];digest[0]=2;
        var app=new AiApplicationRegistryService(()->{try{return connection("publisher",null,null);}catch(SQLException failure){throw new IllegalStateException(failure);}},ISSUER,RESOURCE).publishCimd(external,digest,2,false,null);
        var request=new AiTenantApplicationService(provider("tenant",7,72)).request(app.applicationId(),app.rowVersion(),AiScope.fromMask(3));
        new AiTenantApplicationService(provider("tenant",7,71)).decide(request.approvalId(),request.rowVersion(),AiTenantApplicationService.Decision.APPROVE,AiScope.fromMask(3),1);
        var keygen=java.security.KeyPairGenerator.getInstance("RSA");keygen.initialize(2048);var pair=keygen.generateKeyPair();
        byte[] modulus=((java.security.interfaces.RSAPublicKey)pair.getPublic()).getModulus().toByteArray();if(modulus[0]==0)modulus=Arrays.copyOfRange(modulus,1,modulus.length);
        String keys="[{\"kid\":\"remote-not-a-uuid\",\"modulus\":\""+HexFormat.of().formatHex(modulus)+"\",\"exponent\":65537}]";
        neutral("cimdmetadata").publishMetadata(app.applicationId(),1,digest,"[\""+CALLBACK+"\"]",keys,Instant.now().plusSeconds(240));
        UUID web=UUID.randomUUID(),tx=UUID.randomUUID();String nonce=AiOAuthCredentialStore.opaque(),code=AiOAuthCredentialStore.opaque();
        try(Connection c=owner(db);var p=c.prepareStatement("INSERT dbo.UserSessions(SessionId,ShaleClientId,UserId,ClientType,CurrentAccessJti,IssuedAt,ExpiresAt) VALUES(?,7,72,'WEB',NEWID(),SYSUTCDATETIME(),DATEADD(hour,2,SYSUTCDATETIME()))")){p.setObject(1,web);p.executeUpdate();}
        browser(7,72).begin(tx,web,external,CALLBACK,AiOAuthExchangeStore.challenge(VERIFIER),"private client state",AiScope.fromMask(3),nonce);
        browser(7,72).approve(tx,web,AiScope.fromMask(3));browser(7,72).complete(tx,web,AiScope.fromMask(3),code);
        var selected=issuer(7,72).clientProof(code,external,"CODE");assertEquals("private_key_jwt",selected.method());assertEquals("remote-not-a-uuid",selected.kid());assertNotNull(selected.keyId());
        assertThrows(RuntimeException.class,()->issuer(7,72).redeem(code,external,CALLBACK,VERIFIER,null,null,null,null),"Confidential client cannot fall back to public authentication.");
        var now=Instant.now().minusSeconds(1);var expires=now.plusSeconds(60);
        try(Connection c=owner(db)){sql(c,"CREATE TRIGGER dbo.Pr4bAssertionAuditFault ON dbo.EntityActionAuditLog AFTER INSERT AS THROW 59999,'Synthetic audit fault',1");}
        try{assertThrows(RuntimeException.class,()->issuer(7,72).redeem(code,external,CALLBACK,VERIFIER,selected.keyId(),"first-jti",now,expires));}
        finally{try(Connection c=owner(db)){sql(c,"DROP TRIGGER dbo.Pr4bAssertionAuditFault");}}
        var tokens=issuer(7,72).redeem(code,external,CALLBACK,VERIFIER,selected.keyId(),"first-jti",now,expires);
        read(7,72,tokens.accessToken(),100);
        var bRequest=new AiTenantApplicationService(provider("tenant",8,82)).request(app.applicationId(),app.rowVersion(),AiScope.fromMask(3));
        new AiTenantApplicationService(provider("tenant",8,81)).decide(bRequest.approvalId(),bRequest.rowVersion(),AiTenantApplicationService.Decision.APPROVE,AiScope.fromMask(3),1);
        UUID bWeb=UUID.randomUUID(),bTx=UUID.randomUUID();String bCode=AiOAuthCredentialStore.opaque();
        try(Connection c=owner(db);var p=c.prepareStatement("INSERT dbo.UserSessions(SessionId,ShaleClientId,UserId,ClientType,CurrentAccessJti,IssuedAt,ExpiresAt) VALUES(?,8,82,'WEB',NEWID(),SYSUTCDATETIME(),DATEADD(hour,2,SYSUTCDATETIME()))")){p.setObject(1,bWeb);p.executeUpdate();}
        browser(8,82).begin(bTx,bWeb,external,CALLBACK,AiOAuthExchangeStore.challenge(VERIFIER),"B private state",AiScope.fromMask(3),AiOAuthCredentialStore.opaque());
        browser(8,82).approve(bTx,bWeb,AiScope.fromMask(3));browser(8,82).complete(bTx,bWeb,AiScope.fromMask(3),bCode);
        var bKey=issuer(8,82).clientProof(bCode,external,"CODE");
        assertThrows(RuntimeException.class,()->issuer(8,82).redeem(bCode,external,CALLBACK,VERIFIER,bKey.keyId(),"first-jti",now,expires),"The same external assertion cannot replay in a different tenant.");
        var bTokens=issuer(8,82).redeem(bCode,external,CALLBACK,VERIFIER,bKey.keyId(),"B-jti",now,expires);read(8,82,bTokens.accessToken(),200);
        assertThrows(RuntimeException.class,()->neutral("cimdneutral").revokeUnknown(external,"private_key_jwt",selected.kid(),"first-jti",now,expires),"Known and unknown-token revocation share the global replay boundary.");
        neutral("cimdneutral").revokeUnknown(external,"private_key_jwt",selected.kid(),"unknown-jti",now,expires);
        neutral("cimdneutral").revokeUnknown(EXTERNAL,"none",null,null,null,null);
        read(7,72,tokens.accessToken(),100);read(8,82,bTokens.accessToken(),200);
        assertThrows(RuntimeException.class,()->issuer(7,72).refresh(tokens.refreshToken(),external,selected.keyId(),"first-jti",now,expires),"The same JTI cannot be reused at refresh.");
        var rotated=issuer(7,72).refresh(tokens.refreshToken(),external,selected.keyId(),"second-jti",now,expires);read(7,72,rotated.accessToken(),100);
        issuer(7,72).revoke(rotated.refreshToken(),external,selected.keyId(),"third-jti",now,expires);assertThrows(RuntimeException.class,()->cimd(rotated.accessToken()));manualRead();
    }
    @Test void lifecycleExpiredCodeAndRefreshDoNotResurrectAuthority()throws Exception {
        var f=consent(7,72);var live=redeem(f);String oldCode=AiOAuthCredentialStore.opaque(),oldRefresh=AiOAuthCredentialStore.opaque();
        byte[] intent=AiOAuthCredentialStore.digest(AiOAuthCredentialStore.opaque());
        try(Connection c=owner(db)) {
            try(var p=c.prepareStatement("INSERT dbo.AiConsentIntent(ShaleClientId,IntegrationId,UserId,IntentHash,ScopeMask,CreatedAt,ExpiresAt,GrantExpiresAt,ConsumedAt) SELECT ShaleClientId,IntegrationId,UserId,?,RequestedScopeMask,DATEADD(minute,-4,SYSUTCDATETIME()),DATEADD(minute,1,SYSUTCDATETIME()),DATEADD(minute,30,SYSUTCDATETIME()),DATEADD(minute,-3,SYSUTCDATETIME()) FROM dbo.AiCimdAuthorizationV1 WHERE TransactionId=?")){p.setBytes(1,intent);p.setObject(2,f.transaction());assertEquals(1,p.executeUpdate());}
            try(var p=c.prepareStatement("INSERT dbo.AiCimdAuthorizationV1(TransactionId,ShaleClientId,IntegrationId,UserId,WebSessionId,IntentHash,ClientId,Issuer,Resource,RedirectUri,Challenge,ClientState,RequestedScopeMask,ApprovedScopeMask,RegistrationVersion,CredentialId,CreatedAt,IntentExpiresAt,GrantId,CodeHash,IssuedAt,CodeExpiresAt,ApplicationId,ApplicationRevision,ApprovalId,ApprovalVersion,MetadataDigest,ExternalClientId,AuthMethod) SELECT NEWID(),ShaleClientId,IntegrationId,UserId,WebSessionId,?,ClientId,Issuer,Resource,RedirectUri,Challenge,ClientState,RequestedScopeMask,ApprovedScopeMask,RegistrationVersion,CredentialId,DATEADD(minute,-4,CreatedAt),DATEADD(minute,1,CreatedAt),GrantId,?,DATEADD(minute,-2,IssuedAt),DATEADD(minute,-2,CodeExpiresAt),ApplicationId,ApplicationRevision,ApprovalId,ApprovalVersion,MetadataDigest,ExternalClientId,AuthMethod FROM dbo.AiCimdAuthorizationV1 WHERE TransactionId=?")){p.setBytes(1,intent);p.setBytes(2,AiOAuthCredentialStore.digest(oldCode));p.setObject(3,f.transaction());assertEquals(1,p.executeUpdate());}
            try(var p=c.prepareStatement("INSERT dbo.AiCimdCredentialV1(ShaleClientId,IntegrationId,UserId,GrantId,SessionId,CredentialHash,Kind,ScopeMask,Generation,IssuedAt,ExpiresAt) SELECT ShaleClientId,IntegrationId,UserId,GrantId,SessionId,?,'REFRESH',ScopeMask,Generation,DATEADD(minute,-2,IssuedAt),DATEADD(minute,-1,IssuedAt) FROM dbo.AiCimdCredentialV1 WHERE CredentialHash=?")){p.setBytes(1,AiOAuthCredentialStore.digest(oldRefresh));p.setBytes(2,AiOAuthCredentialStore.digest(live.refreshToken()));assertEquals(1,p.executeUpdate());}
        }
        assertThrows(RuntimeException.class,()->issuer(7,72).redeem(oldCode,EXTERNAL,CALLBACK,VERIFIER,null,null,null,null));
        assertThrows(RuntimeException.class,()->issuer(7,72).refresh(oldRefresh,EXTERNAL,null,null,null,null));read(7,72,live.accessToken(),100);
        read(7,72,issuer(7,72).refresh(live.refreshToken(),EXTERNAL,null,null,null,null).accessToken(),100);manualRead();
    }
    @Test void lifecycleInstallerLateFaultAndRuntimePrivilegesFailClosed()throws Exception {
        var fa=begin(7,72);var fb=begin(8,82);
        for(String role:List.of("cimdbrowser","cimdissuer","cimdresolver","cimdmetadata"))try(Connection c=connection(role,null,null)) {
            for(String table:List.of("AiCimdAuthorizationV1","AiCimdFamilyV1","AiCimdCredentialV1","AiOAuthCredential"))assertEquals(229,assertThrows(SQLException.class,()->sql(c,"SELECT * FROM dbo."+table)).getErrorCode());
            assertEquals(229,assertThrows(SQLException.class,()->sql(c,"EXEC dbo.AiIssueCredentialsV1")).getErrorCode());
            assertEquals(229,assertThrows(SQLException.class,()->sql(c,"EXEC dbo.AiCimdIssueV1")).getErrorCode(),"Private issue helper cannot be called directly.");
        }
        try(Connection c=owner(db)){sql(c,"GRANT SELECT ON dbo.AiCimdAuthorizationV1 TO ["+prefix+"_cimdprobe]");}
        try {
            try(Connection c=connection("cimdprobe",7,72)){assertEquals(1,count(c,"SELECT COUNT(*) FROM dbo.AiCimdAuthorizationV1"));assertEquals(0,count(c,"SELECT COUNT(*) FROM dbo.AiCimdAuthorizationV1 WHERE TransactionId='"+fb.transaction()+"'"));}
            try(Connection c=connection("cimdprobe",null,null)){assertEquals(0,count(c,"SELECT COUNT(*) FROM dbo.AiCimdAuthorizationV1"));}
        }finally{try(Connection c=owner(db)){sql(c,"REVOKE SELECT ON dbo.AiCimdAuthorizationV1 FROM ["+prefix+"_cimdprobe]");}}
        try(Connection c=owner(db)) {
            assertThrows(SQLException.class,()->sql(c,"UPDATE dbo.AiCimdAuthorizationV1 SET ExternalClientId=N'https://other.test/client.json' WHERE TransactionId='"+fa.transaction()+"'"));
            assertThrows(SQLException.class,()->sql(c,"UPDATE dbo.AiCimdOAuthReceiptV1 SET SourceHash=0x00"));
            installSql(c,Files.readString(Path.of("../docs/sql/ai-integration/universal-pr4b-oauth-verify.sql")));
        }
        manualRead();
    }
    @Test void lifecycleOffAndExpiredWebDenyIssuanceAndRefresh()throws Exception {
        var f=consent(7,72);var ta=redeem(f);var fb=consent(8,82);
        try(Connection c=owner(db)){sql(c,"UPDATE dbo.AiCimdOAuthControlV1 SET Enabled=0 WHERE Id=1");}
        assertThrows(RuntimeException.class,()->redeem(fb));assertThrows(RuntimeException.class,()->issuer(7,72).refresh(ta.refreshToken(),EXTERNAL,null,null,null,null));manualRead();
        try(Connection c=owner(db)){sql(c,"UPDATE dbo.AiCimdOAuthControlV1 SET Enabled=1 WHERE Id=1; UPDATE dbo.UserSessions SET RevokedAt=SYSUTCDATETIME() WHERE SessionId='"+f.web()+"'");}
        assertThrows(RuntimeException.class,()->issuer(7,72).refresh(ta.refreshToken(),EXTERNAL,null,null,null,null));read(8,82,redeem(fb).accessToken(),200);
    }
}
