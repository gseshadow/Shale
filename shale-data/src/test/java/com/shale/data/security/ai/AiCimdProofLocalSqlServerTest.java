package com.shale.data.security.ai;

import com.shale.core.security.ai.AiScope;
import org.junit.jupiter.api.*;
import javax.sql.DataSource;
import java.lang.reflect.*;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

/** Actual restricted JDBC/authoritative V2 proof tests. Owner-seeded exchange receipts are NOT an issuer. */
class AiCimdProofLocalSqlServerTest extends AiApplicationConfinementV2LocalSqlServerTest {
    static final String EXTERNAL="https://vendor.test/oauth/client.json";
    AiApplicationRegistryService.Application application;
    AiTenantApplicationService.Approval approvalA,approvalB;
    Family a,b;
    Map<String,String> historical;
    String immutableBinding;
    record Family(int tenant,int user,UUID session,String access,String refresh,String code) {
        @Override public String toString(){return "SyntheticCimdFamily[redacted]";}
    }
    @BeforeAll void proofIdentities() throws Exception {
        try(Connection c=owner("master")){for(String role:List.of("cimd","cimdlife","cimdprobe")){
            String secret=UUID.randomUUID()+"aB9!";
            sql(c,"CREATE LOGIN ["+prefix+"_"+role+"] WITH PASSWORD='"+secret+"',CHECK_POLICY=OFF");secrets.put(role,secret);
        }}
    }
    @BeforeEach void proofFoundation(TestInfo info) throws Exception {
        installCompatibility();
        try(Connection c=owner(db)) {
            historical=receipts(c);immutableBinding=binding(c);
            String candidate=Files.readString(Path.of("../docs/sql/ai-integration/universal-pr4a-proof.sql"));
            if(info.getTestMethod().orElseThrow().getName().equals("independentReceiptRejectsDriftWithoutRelaxingHistoricalSnapshot")){
                String injected=candidate.replace("DECLARE @C4aSealVerify", "GRANT EXECUTE ON dbo.AiCimdDataLoadAuthorityV1 TO AiCimdCaseRuntimeV1;\nDECLARE @C4aSealVerify");
                assertNotEquals(candidate,injected);
                assertThrows(SQLException.class,()->sql(c,injected),"A late permission fault must roll back the new receipt and all DDL before COMMIT.");
                assertEquals(0,count(c,"SELECT COUNT(*) FROM sys.tables WHERE name LIKE 'AiCimd%'") );
                assertEquals(0,count(c,"SELECT COUNT(*) FROM sys.database_principals WHERE name IN('AiCimdCaseRuntimeV1','AiCimdLifecycleV1')"));
                assertEquals(historical,receipts(c));assertEquals(immutableBinding,binding(c));
            }
            sql(c,candidate);
            assertEquals(0,count(c,"SELECT CONVERT(int,Enabled) FROM dbo.AiCimdProofControlV1"),"Candidate must install default OFF.");
            sql(c,"ALTER ROLE AiCimdCaseRuntimeV1 ADD MEMBER ["+prefix+"_cimd]; ALTER ROLE AiCimdLifecycleV1 ADD MEMBER ["+prefix+"_cimdlife]");
            sql(c,Files.readString(Path.of("../docs/sql/ai-integration/universal-pr4a-proof-verify.sql")));
            sql(c,"UPDATE dbo.AiProductCapability SET Enabled=1 WHERE Id=1; UPDATE dbo.AiCimdProofControlV1 SET Enabled=1 WHERE Id=1");
        }
        application=publishApplication();
        approvalA=approve(7,72,71);approvalB=approve(8,82,81);
        a=seed(approvalA,7,72);b=seed(approvalB,8,82);
    }
    @AfterEach void unchangedHistoricalContracts()throws Exception {
        if(db==null||historical==null)return;
        try(Connection c=owner(db)){
            for(var p:historical.entrySet())assertEquals(p.getValue(),receipts(c).get(p.getKey()),"Historical receipt changed: "+p.getKey());
            assertEquals(immutableBinding,binding(c));
        }
    }
    AiTenantApplicationService.Approval approve(int tenant,int user,int admin){
        var service=new AiTenantApplicationService(provider("tenant",tenant,user));
        var request=service.request(application.applicationId(),application.rowVersion(),AiScope.fromMask(3));
        return new AiTenantApplicationService(provider("tenant",tenant,admin)).decide(request.approvalId(),request.rowVersion(),AiTenantApplicationService.Decision.APPROVE,AiScope.fromMask(3),1);
    }
    Family seed(AiTenantApplicationService.Approval approval,int tenant,int user)throws Exception {
        UUID session=UUID.randomUUID(),grant=UUID.randomUUID(),web=UUID.randomUUID();
        String access=AiOAuthCredentialStore.opaque(),refresh=AiOAuthCredentialStore.opaque(),code=AiOAuthCredentialStore.opaque();
        try(Connection c=owner(db)) {
            // Synthetic exchange receipt only: grant/session created by fixture owner, never by approval.
            try(var p=c.prepareStatement("INSERT dbo.UserSessions(SessionId,ShaleClientId,UserId,ClientType,CurrentAccessJti,IssuedAt,ExpiresAt) VALUES(?,?,?,'WEB',NEWID(),SYSUTCDATETIME(),DATEADD(hour,2,SYSUTCDATETIME()))")){
                p.setObject(1,web);p.setInt(2,tenant);p.setInt(3,user);p.executeUpdate();
            }
            try(var p=c.prepareStatement("DECLARE @now datetime2(7)=SYSUTCDATETIME(); INSERT dbo.AiDelegationGrant(ShaleClientId,IntegrationId,UserId,GrantId,ScopeMask,ConsentedAt,ExpiresAt) VALUES(?,?,?,?,3,@now,DATEADD(hour,1,@now)); INSERT dbo.AiDelegatedSession(ShaleClientId,IntegrationId,UserId,GrantId,SessionId,ScopeMask,IssuedAt,ExpiresAt,IdleExpiresAt,ClientId,Issuer,Resource,ConsentAt,CapabilityRevision) SELECT ShaleClientId,IntegrationId,?,?,?,3,@now,DATEADD(hour,1,@now),DATEADD(hour,1,@now),ClientId,Issuer,Resource,@now,(SELECT Revision FROM dbo.AiProductCapability WHERE Id=1) FROM dbo.AiIntegrationRegistration WHERE ShaleClientId=? AND IntegrationId=?")) {
                p.setInt(1,tenant);p.setObject(2,approval.integrationId());p.setInt(3,user);p.setObject(4,grant);
                p.setInt(5,user);p.setObject(6,grant);p.setObject(7,session);p.setInt(8,tenant);p.setObject(9,approval.integrationId());p.executeUpdate();
            }
            try(var p=c.prepareStatement("INSERT dbo.AiCimdFamilyV1(ShaleClientId,SessionId,IntegrationId,GrantId,UserId,TransactionId,WebSessionId,ApplicationId,ApplicationRevision,ApprovalId,ApprovalVersion,MetadataDigest,ExternalClientId,ClientId,Issuer,Resource,AuthMethod,RedirectUri,Challenge,CodeHash,CodeIssuedAt,CodeExpiresAt,ConsumedAt) SELECT s.ShaleClientId,s.SessionId,s.IntegrationId,s.GrantId,s.UserId,NEWID(),?,p.ApplicationId,p.ApplicationRevision,p.ApprovalId,p.RowVer,e.MetadataDigest,e.ExternalClientId,s.ClientId,s.Issuer,s.Resource,'none',?,?,?,DATEADD(second,-1,SYSUTCDATETIME()),DATEADD(second,59,SYSUTCDATETIME()),SYSUTCDATETIME() FROM dbo.AiDelegatedSession s JOIN dbo.AiTenantApplicationApproval p ON p.ShaleClientId=s.ShaleClientId AND p.IntegrationId=s.IntegrationId JOIN dbo.AiExternalApplication e ON e.ApplicationId=p.ApplicationId WHERE s.SessionId=?")) {
                p.setObject(1,web);p.setString(2,CALLBACK);p.setString(3,AiOAuthExchangeStore.challenge(VERIFIER));p.setBytes(4,AiOAuthCredentialStore.digest(code));p.setObject(5,session);assertEquals(1,p.executeUpdate());
            }
            try(var p=c.prepareStatement("INSERT dbo.AiCimdCredentialV1(ShaleClientId,IntegrationId,UserId,GrantId,SessionId,CredentialHash,Kind,ScopeMask,Generation,IssuedAt,ExpiresAt) SELECT ShaleClientId,IntegrationId,UserId,GrantId,SessionId,?, ?,3,0,SYSUTCDATETIME(),DATEADD(minute,5,SYSUTCDATETIME()) FROM dbo.AiDelegatedSession WHERE SessionId=?")) {
                for(var entry:List.of(Map.entry(access,"ACCESS"),Map.entry(refresh,"REFRESH"))){p.setBytes(1,AiOAuthCredentialStore.digest(entry.getKey()));p.setString(2,entry.getValue());p.setObject(3,session);assertEquals(1,p.executeUpdate());}
            }
        }
        return new Family(tenant,user,session,access,refresh,code);
    }
    DataSource cimdSource(){ return source("cimd"); }
    static Object invoke(Object target,Method method,Object[] args)throws Throwable {
        try{return method.invoke(target,args);}catch(InvocationTargetException failure){throw failure.getCause();}
    }
    AiCaseDataRequest cimd(String access){return AiCaseDataAccess.authenticate(cimdSource(),access,ISSUER,RESOURCE,System.nanoTime()+10_000_000_000L,AiCaseDataAccess.Namespace.CIMD_V1);}
    void ownRead(Family f,long id){try(var request=cimd(f.access())){
        assertEquals(f.tenant(),request.authority().shaleClientId());
        var invocation=IntegrationInvocation.overview(request.authority(),System.nanoTime()+8_000_000_000L);
        assertTrue(new AiCaseDataAccess(cimdSource(),cimdSource(),request,AiCaseDataAccess.Namespace.CIMD_V1).overview(invocation,id,DisclosureBuffer::size).size()>0);
    }}
    void manualRead(){try(var request=authenticate(token)){
        var invocation=IntegrationInvocation.overview(request.authority(),System.nanoTime()+8_000_000_000L);
        assertTrue(new AiCaseDataAccess(source("ai"),source("ai"),request).overview(invocation,100L,DisclosureBuffer::size).size()>0);
    }}
    AiApplicationRegistryService.Application republish(boolean disabled){byte[] digest=new byte[32];digest[0]=1;
        return new AiApplicationRegistryService(()->{try{return connection("publisher",null,null);}catch(SQLException failure){throw new IllegalStateException(failure);}},ISSUER,RESOURCE).publishCimd(EXTERNAL,digest,1,disabled,application.rowVersion());
    }
    @Test void namespaceAndTwoTenantCaseProofPreserveManualClients()throws Exception {
        ownRead(a,100);ownRead(b,200);manualRead();
        assertThrows(RuntimeException.class,()->authenticate(a.access()),"Legacy proof must never admit a CIMD credential.");
        assertThrows(RuntimeException.class,()->cimd(token),"The new namespace must not treat a manual token as CIMD.");
        assertThrows(RuntimeException.class,()->cimd(a.refresh()),"Refresh is not Case proof.");
        try(var request=cimd(a.access())){
            var invocation=IntegrationInvocation.overview(request.authority(),System.nanoTime()+8_000_000_000L);
            assertThrows(IntegrationReadException.class,()->new AiCaseDataAccess(cimdSource(),cimdSource(),request,AiCaseDataAccess.Namespace.CIMD_V1).overview(invocation,200L,bf->fail("No foreign Case may reach serialization.")));
        }
        try(Connection c=owner(db)){sql(c,Files.readString(Path.of("../docs/sql/ai-integration/universal-pr4a-proof-verify.sql")));}
        identitiesAttestation().verify();attestation().verify();
    }
    @Test void publicationChangeDeniesBothTenantsAtAdmissionAndFinalDisclosure()throws Exception {
        ownRead(b,200);
        try(var request=cimd(a.access())){
            var invocation=IntegrationInvocation.overview(request.authority(),System.nanoTime()+8_000_000_000L);
            assertThrows(IntegrationReadException.class,()->new AiCaseDataAccess(cimdSource(),cimdSource(),request,AiCaseDataAccess.Namespace.CIMD_V1).overview(invocation,100L,buffer->{republish(false);return buffer.size();}),
                    "Publication changes after audited SQL read must prevent final disclosure.");
        }
        assertThrows(RuntimeException.class,()->cimd(a.access()));assertThrows(RuntimeException.class,()->cimd(b.access()));manualRead();
    }
    @Test void disabledApplicationFailsClosed()throws Exception {
        republish(true);assertThrows(RuntimeException.class,()->cimd(a.access()));assertThrows(RuntimeException.class,()->cimd(b.access()));manualRead();
    }
    byte[] sessionVersion(Family f)throws Exception{try(Connection c=owner(db);var p=c.prepareStatement("SELECT RowVer FROM dbo.AiDelegatedSession WHERE SessionId=?")){
        p.setObject(1,f.session());try(var r=p.executeQuery()){assertTrue(r.next());return r.getBytes(1);}
    }}
    void revoke(Family f,int tenant,int user,byte[] version){AiSql.call(provider("cimdlife",tenant,user),"AiCimdRevokeFamilyV1",r->true,f.session(),version,UUID.randomUUID());}
    @Test void concurrentRevocationHasOneWinnerAndDoesNotAffectOtherTenant()throws Exception {
        byte[] version=sessionVersion(a);var pool=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);
        try{
            List<Future<Boolean>> results=new ArrayList<>();
            for(int n=0;n<2;n++)results.add(pool.submit(()->{start.await();try{revoke(a,7,72,version);return true;}catch(IllegalStateException denied){return false;}}));
            start.countDown();int successes=0;for(var result:results)if(result.get(15,TimeUnit.SECONDS))successes++;
            assertEquals(1,successes,"RowVer and tenant lock must serialize competing revocations.");
        }finally{pool.shutdownNow();}
        assertThrows(RuntimeException.class,()->cimd(a.access()));ownRead(b,200);manualRead();
        assertThrows(RuntimeException.class,()->revoke(b,7,71,sessionVersionUnchecked(b)),"Tenant A cannot revoke B.");
    }
    byte[] sessionVersionUnchecked(Family f){try{return sessionVersion(f);}catch(Exception failure){throw new IllegalStateException(failure);}}
    @Test void failedAuditRollsBackRevocationAndCaseReadReceipt()throws Exception {
        byte[] version=sessionVersion(a);
        try(Connection c=owner(db)){sql(c,"CREATE TRIGGER dbo.Pr4aAuditFault ON dbo.EntityActionAuditLog AFTER INSERT AS THROW 59999,'Synthetic audit fault',1");}
        try{assertThrows(IllegalStateException.class,()->revoke(a,7,72,version));assertArrayEquals(version,sessionVersion(a));}
        finally{try(Connection c=owner(db)){sql(c,"DROP TRIGGER dbo.Pr4aAuditFault");}}
        ownRead(a,100);ownRead(b,200);
        try(Connection c=owner(db)){sql(c,"CREATE TRIGGER dbo.Pr4aReadFault ON dbo.IntegrationAccessAuditLog AFTER INSERT AS THROW 59999,'Synthetic audit fault',1");}
        try(var request=cimd(a.access())){
            var invocation=IntegrationInvocation.overview(request.authority(),System.nanoTime()+8_000_000_000L);
            assertThrows(IntegrationReadException.class,()->new AiCaseDataAccess(cimdSource(),cimdSource(),request,AiCaseDataAccess.Namespace.CIMD_V1).overview(invocation,100L,buffer->fail("Uncommitted audit must withhold data.")));
        }finally{try(Connection c=owner(db)){sql(c,"DROP TRIGGER dbo.Pr4aReadFault");}}
        manualRead();
    }
    @Test void approvalRevocationBetweenReadAndHandoffIsTenantConfined() throws Exception {
        try(var request=cimd(a.access())){
            var invocation=IntegrationInvocation.overview(request.authority(),System.nanoTime()+8_000_000_000L);
            assertThrows(IntegrationReadException.class,()->new AiCaseDataAccess(cimdSource(),cimdSource(),request,AiCaseDataAccess.Namespace.CIMD_V1).overview(invocation,100L,buffer->{
                new AiTenantApplicationService(provider("tenant",7,71)).decide(approvalA.approvalId(),approvalA.rowVersion(),AiTenantApplicationService.Decision.REVOKE,Set.of(),0);
                return buffer.size();
            }),"Revocation after SQL read must deny final disclosure.");
        }
        assertThrows(RuntimeException.class,()->cimd(a.access()));ownRead(b,200);manualRead();
    }
    @Test void expiredApprovalScopeReductionAndOffControlDenyNewAuthority() throws Exception {
        try(Connection c=owner(db)){sql(c,"UPDATE dbo.AiCimdProofControlV1 SET Enabled=0 WHERE Id=1");}
        assertThrows(RuntimeException.class,()->cimd(a.access()));assertThrows(RuntimeException.class,()->cimd(b.access()));manualRead();
        try(Connection c=owner(db)){
            sql(c,"UPDATE dbo.AiCimdProofControlV1 SET Enabled=1 WHERE Id=1");
            sql(c,"UPDATE dbo.AiTenantApplicationApproval SET ExpiresAt=DATEADD(microsecond,1,CreatedAt) WHERE ApprovalId='"+approvalA.approvalId()+"'");
        }
        assertThrows(RuntimeException.class,()->cimd(a.access()),"Expired approval must not admit proof.");ownRead(b,200);
        new AiTenantApplicationService(provider("tenant",8,81)).decide(approvalB.approvalId(),approvalB.rowVersion(),AiTenantApplicationService.Decision.REDUCE,AiScope.fromMask(1),0);
        assertThrows(RuntimeException.class,()->cimd(b.access()),"Reduced ceiling must not preserve the prior broader family.");manualRead();
    }
    @Test void restrictedIdentitiesCannotUseLegacyCodeRefreshOrDirectTables() throws Exception {
        for(String role:List.of("cimd","cimdlife"))try(Connection c=connection(role,8,81)) {
            for(String table:List.of("Cases","AiCimdFamilyV1","AiCimdCredentialV1","AiCimdProofReceiptV1","AiOAuthCredential"))
                assertEquals(229,assertThrows(SQLException.class,()->sql(c,"SELECT * FROM dbo."+table)).getErrorCode(),table);
            assertEquals(229,assertThrows(SQLException.class,()->sql(c,"EXEC dbo.AiDataAuthenticateV1")).getErrorCode());
        }
        com.shale.core.runtime.DbSessionProvider bootstrap=()->{try{return connection("bootstrap",null,null);}catch(SQLException failure){throw new IllegalStateException(failure);}};
        String internal;
        try(Connection c=owner(db);var p=c.prepareStatement("SELECT ClientId FROM dbo.AiCimdFamilyV1 WHERE SessionId=?")){
            p.setObject(1,a.session());try(var r=p.executeQuery()){assertTrue(r.next());internal=r.getString(1);}
        }
        assertTrue(new AiOAuthTransactionStore(provider("runtime",7,72),true,ISSUER,RESOURCE).loadClient(internal).isEmpty(),"The CIMD integration must stay unavailable to the old client loader.");
        assertThrows(IllegalStateException.class,()->controls(7,72).beginConsent(approvalA.integrationId(),AiScope.fromMask(3),1),"Old consent cannot mint another legacy family for a CIMD integration.");
        assertTrue(new AiOAuthExchangeStore(bootstrap,ISSUER,RESOURCE).resolve(a.code(),internal).isEmpty(),"New code digest must not enter the legacy exchange.");
        assertTrue(new AiOAuthCredentialStore(bootstrap,ISSUER,RESOURCE).resolveRefresh(a.refresh(),internal).isEmpty(),"New refresh digest must not enter legacy rotation.");
        manualRead();
    }
    @Test void independentReceiptRejectsDriftWithoutRelaxingHistoricalSnapshot() throws Exception {
        byte[] sourceHash;
        try(Connection c=owner(db)){
            try(var st=c.createStatement();var r=st.executeQuery("SELECT SourceHash FROM dbo.AiCimdProofReceiptV1")){assertTrue(r.next());sourceHash=r.getBytes(1);}
            assertThrows(SQLException.class,()->sql(c,"UPDATE dbo.AiCimdProofReceiptV1 SET SourceHash=0x00"),"New receipt is immutable.");
            sql(c,"UPDATE dbo.AiProductCapability SET Enabled=0 WHERE Id=1");
            assertThrows(SQLException.class,()->sql(c,Files.readString(Path.of("../docs/sql/ai-integration/universal-pr4a-proof.sql"))),"Installation must not reseal an existing extension.");
            sql(c,"EXEC sys.sp_addextendedproperty @name=N'Shale.PR4a.Unreviewed',@value=1");
            assertThrows(SQLException.class,()->sql(c,Files.readString(Path.of("../docs/sql/ai-integration/universal-pr3-confinement-verify.sql"))),"The frozen #1948 receipt set must still reject an added Shale property.");
            sql(c,"EXEC sys.sp_dropextendedproperty @name=N'Shale.PR4a.Unreviewed'");
            sql(c,Files.readString(Path.of("../docs/sql/ai-integration/universal-pr4a-proof-verify.sql")));
            sql(c,"ALTER ROLE db_datareader ADD MEMBER ["+prefix+"_cimd]");
            assertThrows(SQLException.class,()->sql(c,Files.readString(Path.of("../docs/sql/ai-integration/universal-pr4a-proof-verify.sql"))),"Inherited authority must fail the new verifier.");
            sql(c,"ALTER ROLE db_datareader DROP MEMBER ["+prefix+"_cimd]");
            sql(c,Files.readString(Path.of("../docs/sql/ai-integration/universal-pr4a-proof-verify.sql")));
            sql(c,"CREATE PROCEDURE dbo.Pr4aUnexpectedRoot AS SELECT 1 AS UnexpectedValue");
            sql(c,"GRANT EXECUTE ON dbo.Pr4aUnexpectedRoot TO AiCimdCaseRuntimeV1");
            assertThrows(SQLException.class,()->sql(c,Files.readString(Path.of("../docs/sql/ai-integration/universal-pr4a-proof-verify.sql"))),"Extra new-role EXECUTE must be rejected explicitly.");
            sql(c,"REVOKE EXECUTE ON dbo.Pr4aUnexpectedRoot FROM AiCimdCaseRuntimeV1; DROP PROCEDURE dbo.Pr4aUnexpectedRoot");
            sql(c,Files.readString(Path.of("../docs/sql/ai-integration/universal-pr4a-proof-verify.sql")));
            sql(c,"ALTER PROCEDURE dbo.AiCimdDataAuthenticateV1 AS THROW 59999,'Synthetic source drift',1");
            assertThrows(SQLException.class,()->sql(c,Files.readString(Path.of("../docs/sql/ai-integration/universal-pr4a-proof-verify.sql"))),"Source drift must not be adopted.");
            try(var st=c.createStatement();var r=st.executeQuery("SELECT SourceHash FROM dbo.AiCimdProofReceiptV1")){assertTrue(r.next());assertArrayEquals(sourceHash,r.getBytes(1));}
        }
    }

    @Test void strictRlsAndOwnerImmutabilityProtectNewNamespace() throws Exception {
        try(Connection c=owner(db)){
            sql(c,"GRANT SELECT ON dbo.AiCimdFamilyV1 TO ["+prefix+"_cimdprobe]; GRANT INSERT ON dbo.AiCimdCredentialV1 TO ["+prefix+"_cimdprobe]");
        }
        try {
            try(Connection c=connection("cimdprobe",7,72)){
                assertEquals(1,count(c,"SELECT COUNT(*) FROM dbo.AiCimdFamilyV1"));
                assertEquals(0,count(c,"SELECT COUNT(*) FROM dbo.AiCimdFamilyV1 WHERE SessionId='"+b.session()+"'"),"RLS must hide the other tenant's family.");
            }
            try(Connection c=connection("cimdprobe",null,null)){assertEquals(0,count(c,"SELECT COUNT(*) FROM dbo.AiCimdFamilyV1"));}
            UUID foreignGrant;
            try(Connection c=owner(db);var p=c.prepareStatement("SELECT GrantId FROM dbo.AiCimdFamilyV1 WHERE SessionId=?")){
                p.setObject(1,b.session());try(var r=p.executeQuery()){assertTrue(r.next());foreignGrant=AiSql.uuid(r,"GrantId");}
            }
            try(Connection c=connection("cimdprobe",7,72);var p=c.prepareStatement("INSERT dbo.AiCimdCredentialV1(ShaleClientId,IntegrationId,UserId,GrantId,SessionId,CredentialHash,Kind,ScopeMask,Generation,IssuedAt,ExpiresAt) VALUES(8,?,82,?,?,?,'ACCESS',3,0,SYSUTCDATETIME(),DATEADD(minute,1,SYSUTCDATETIME()))")){
                p.setObject(1,approvalB.integrationId());p.setObject(2,foreignGrant);p.setObject(3,b.session());p.setBytes(4,AiOAuthCredentialStore.digest(AiOAuthCredentialStore.opaque()));
                assertEquals(33504,assertThrows(SQLException.class,p::executeUpdate).getErrorCode(),"AFTER INSERT BLOCK must reject a foreign credential owner.");
            }
        } finally {try(Connection c=owner(db)){sql(c,"REVOKE SELECT ON dbo.AiCimdFamilyV1 FROM ["+prefix+"_cimdprobe]; REVOKE INSERT ON dbo.AiCimdCredentialV1 FROM ["+prefix+"_cimdprobe]");}}
        try(Connection c=owner(db)){
            assertThrows(SQLException.class,()->sql(c,"UPDATE dbo.AiCimdFamilyV1 SET ExternalClientId=N'https://other.test/client.json' WHERE SessionId='"+a.session()+"'"));
            assertThrows(SQLException.class,()->sql(c,"UPDATE dbo.AiDelegatedSession SET TransactionId=NEWID() WHERE SessionId='"+a.session()+"'"),"NULL legacy transaction exclusion must be immutable.");
            sql(c,Files.readString(Path.of("../docs/sql/ai-integration/universal-pr4a-proof-verify.sql")));
        }
        byte[] extended=Arrays.copyOf(sessionVersion(a),9);
        assertThrows(IllegalStateException.class,()->revoke(a,7,72,extended),"A longer RowVer must not be truncated into a valid concurrency token.");
        ownRead(a,100);ownRead(b,200);manualRead();
    }

}
