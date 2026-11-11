package com.shale.data.security.ai;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Explicitly selected deadline diagnostic on actual restricted pools and authoritative schema. */
class AiCimdDeadlineSqlServerTest extends AiCimdCatalogV2SqlServerTest {
    @Test void completeAuditedDisclosureWithinOriginalDeadlines()throws Exception {
        foundation();
        try(var runtime=AiCimdRuntime.open(configuration(),forbidden())) {
            try(var c=owner(db)) { sql(c,"UPDATE dbo.AiProductCapability SET Enabled=1; UPDATE dbo.AiCimdProofControlV1 SET Enabled=1; UPDATE dbo.AiCimdOAuthControlV1 SET Enabled=1"); }
            var issued=redeem(consent(7,72));
            long started=System.nanoTime();
            try(var request=runtime.authenticate(issued.accessToken(),started+8_000_000_000L)) {
                long authenticated=System.nanoTime();
                System.err.println("PR4E admissionMillis="+(authenticated-started)/1_000_000);
                var invocation=IntegrationInvocation.overview(request.authority(),started+8_000_000_000L);
                var buffer=runtime.reader(request).overview(invocation,100L,b->{
                    System.err.println("PR4E readCommittedMillis="+(System.nanoTime()-started)/1_000_000);
                    return b.size();
                });
                assertTrue(java.nio.charset.StandardCharsets.UTF_8.decode(buffer.bytes()).toString().contains("\"caseId\":100"));
                audited(request.authority().invocationId(),1);
            } finally { System.err.println("PR4E totalMillis="+(System.nanoTime()-started)/1_000_000); }
        }
    }
    @Test void catalogCostProfile()throws Exception {
        foundation();
        var sources=new java.util.EnumMap<AiCimdIdentity,javax.sql.DataSource>(AiCimdIdentity.class);
        var users=new java.util.EnumMap<AiCimdIdentity,String>(AiCimdIdentity.class);
        java.util.concurrent.atomic.AtomicLong gateNanos=new java.util.concurrent.atomic.AtomicLong();
        roles().forEach((role,key)->{sources.put(role,timedSource(source(key),gateNanos));users.put(role,prefix+"_"+key);});
        var verifier=new AiCimdRuntimeAttestation(sources,users,forbidden(),db,ISSUER,RESOURCE,contract(),catalogBinding);
        long start=System.nanoTime();verifier.verify();
        System.err.println("PR4E profile fullMillis="+(System.nanoTime()-start)/1_000_000+" gateMillis="+gateNanos.get()/1_000_000);
    }

    static javax.sql.DataSource timedSource(javax.sql.DataSource source,java.util.concurrent.atomic.AtomicLong gate) {
        return (javax.sql.DataSource)java.lang.reflect.Proxy.newProxyInstance(javax.sql.DataSource.class.getClassLoader(),new Class<?>[]{javax.sql.DataSource.class},(p,m,a)->{
            Object value=invoke(source,m,a);
            if(!m.getName().equals("getConnection"))return value;
            var connection=(java.sql.Connection)value;
            return java.lang.reflect.Proxy.newProxyInstance(java.sql.Connection.class.getClassLoader(),new Class<?>[]{java.sql.Connection.class},(cp,cm,ca)->{
                Object result=invoke(connection,cm,ca);
                if(!cm.getName().equals("createStatement"))return result;
                var statement=(java.sql.Statement)result;var began=new java.util.concurrent.atomic.AtomicLong();
                return java.lang.reflect.Proxy.newProxyInstance(java.sql.Statement.class.getClassLoader(),new Class<?>[]{java.sql.Statement.class},(sp,sm,sa)->{
                    if(sm.getName().equals("execute") && sa[0].equals("EXEC dbo.AiCimdAttestCatalogV2"))began.set(System.nanoTime());
                    try{return invoke(statement,sm,sa);}
                    finally {if(sm.getName().equals("close") && began.get()!=0) { gate.addAndGet(System.nanoTime()-began.getAndSet(0)); }}
                });
            });
        });
    }

    void enableFixture()throws Exception {
        try(var c=owner(db)) { sql(c,"UPDATE dbo.AiProductCapability SET Enabled=1; UPDATE dbo.AiCimdProofControlV1 SET Enabled=1; UPDATE dbo.AiCimdOAuthControlV1 SET Enabled=1"); }
    }
    AiCaseDataRequest proof(String token,long until) {
        return AiCaseDataAccess.authenticate(source("cimd"),token,ISSUER,RESOURCE,until,AiCaseDataAccess.Namespace.CIMD_V1);
    }
    AiCaseDataAccess proofReader(AiCaseDataRequest request) {
        return new AiCaseDataAccess(source("cimd"),source("cimd"),request,AiCaseDataAccess.Namespace.CIMD_V1);
    }
    void audited(java.util.UUID invocation,int phiCount)throws Exception {
        try(var c=owner(db)) {
            assertEquals(1,count(c,"SELECT COUNT(*) FROM dbo.IntegrationAccessAuditLog WHERE InvocationId='"+invocation+"' AND ShaleClientId=7 AND UserId=72 AND Outcome='ALLOWED_READ' AND Stage='READ'"));
            assertEquals(1,count(c,"SELECT COUNT(*) FROM dbo.IntegrationAccessAuditResource r JOIN dbo.IntegrationAccessAuditLog a ON a.ShaleClientId=r.ShaleClientId AND a.Id=r.EventId WHERE a.InvocationId='"+invocation+"' AND r.Relation='RETURNED' AND r.CaseId=100"));
            assertEquals(phiCount,count(c,"SELECT COUNT(*) FROM dbo.auditLog WHERE FieldName='Case.Overview.Read' AND ShaleClientId=7 AND UserId=72 AND ObjectId=100"));
            assertEquals(1,count(c,"SELECT COUNT(*) FROM dbo.AiCimdInvocationV1 WHERE InvocationId='"+invocation+"' AND ReadEventId IS NOT NULL AND HandoffConsumed=1"));
            assertEquals(1,count(c,"SELECT COUNT(*) FROM dbo.AiQuotaReservation WHERE InvocationId='"+invocation+"' AND RowCost=1 AND ReleasedAt IS NOT NULL"));
        }
    }
    /** Counterexample only: NOT a production adapter or an accepted attestation shortcut. */
    @Test void liveProofBoundaryDoesNotFencePermissionDrift()throws Exception {
        foundation();attestor().verify();enableFixture();
        var issued=redeem(consent(7,72));
        long started=System.nanoTime();
        try(var request=proof(issued.accessToken(),started+8_000_000_000L)) {
            var result=proofReader(request).overview(IntegrationInvocation.overview(request.authority(),started+8_000_000_000L),100L,DisclosureBuffer::size);
            assertTrue(java.nio.charset.StandardCharsets.UTF_8.decode(result.bytes()).toString().contains("\"caseId\":100"));
            assertTrue(System.nanoTime()-started<8_000_000_000L);
            System.err.println("PR4E proofOnlyControlMillis="+(System.nanoTime()-started)/1_000_000);
            audited(request.authority().invocationId(),1);
        }
        String grant="GRANT SELECT ON dbo.Cases(Name) TO ["+prefix+"_cimd]";
        String revoke="REVOKE SELECT ON dbo.Cases(Name) FROM ["+prefix+"_cimd]";
        try {
            long began=System.nanoTime();
            try(var request=proof(issued.accessToken(),began+8_000_000_000L)) {
                var result=proofReader(request).overview(IntegrationInvocation.overview(request.authority(),began+8_000_000_000L),100L,b->{
                    try(var c=owner(db)){sql(c,grant);}
                    catch(Exception error){throw new AssertionError(error);}
                    return b.size();
                });
                assertTrue(result.size()>0,"Counterexample: fresh SQL proof/handoff does not itself reject new catalog grants.");
                audited(request.authority().invocationId(),2);
            }
            try(var c=source("cimd").getConnection()) {assertEquals(1,count(c,"SELECT HAS_PERMS_BY_NAME('dbo.Cases','OBJECT','SELECT','Name','COLUMN')"));}
            assertThrows(IllegalStateException.class,()->attestor().verify(),"Full authority verification correctly rejects the very same state.");
        } finally { try(var c=owner(db)) {sql(c,revoke);} }
        attestor().verify();
    }

    @Test void runtimeRefusesExpiredAdmissionAndPermissionOrCatalogDrift()throws Exception {
        foundation();enableFixture();var issued=redeem(consent(7,72));
        try(var runtime=AiCimdRuntime.open(configuration(),forbidden())) {
            assertThrows(SecurityException.class,()->runtime.authenticate(issued.accessToken(),System.nanoTime()-1));
            try(var c=owner(db)) { assertEquals(0,count(c,"SELECT COUNT(*) FROM dbo.AiCimdInvocationV1"));sql(c,"GRANT SELECT ON dbo.Cases(Name) TO ["+prefix+"_cimd]"); }
            try {
                assertThrows(SecurityException.class,()->runtime.authenticate(issued.accessToken(),System.nanoTime()+8_000_000_000L));
                assertThrows(IllegalStateException.class,runtime::verify,"A drift failure permanently closes the runtime.");
            } finally {try(var c=owner(db)){sql(c,"REVOKE SELECT ON dbo.Cases(Name) FROM ["+prefix+"_cimd]");}}
        }
        try(var runtime=AiCimdRuntime.open(configuration(),forbidden())) {
            try(var c=owner(db)){sql(c,"CREATE TRIGGER dbo.Pr4eCatalogDrift ON dbo.Cases AFTER UPDATE AS RETURN");}
            try {
                assertThrows(SecurityException.class,()->runtime.authenticate(issued.accessToken(),System.nanoTime()+8_000_000_000L));
                assertThrows(IllegalStateException.class,runtime::verify);
            } finally {try(var c=owner(db)){sql(c,"DROP TRIGGER dbo.Pr4eCatalogDrift");}}
        }
        try(var c=owner(db)) {
            assertEquals(0,count(c,"SELECT COUNT(*) FROM dbo.AiCimdInvocationV1"));
            assertEquals(0,count(c,"SELECT COUNT(*) FROM dbo.IntegrationAccessAuditLog WHERE Outcome='ALLOWED_READ'"));
            assertEquals(0,count(c,"SELECT COUNT(*) FROM dbo.auditLog WHERE FieldName='Case.Overview.Read'"));
        }
    }
    /** Isolated SQL lifecycle controls do not establish complete runtime readiness. */
    @Test void proofControlsPreserveConcurrencyRevocationAndDeadline()throws Exception {
        foundation();attestor().verify();enableFixture();
        var issued=redeem(consent(7,72));var other=redeem(consent(8,82));
        var workers=java.util.concurrent.Executors.newFixedThreadPool(2);
        var start=new java.util.concurrent.CountDownLatch(1);
        try {
            var first=workers.submit(()->{start.await();return controlRead(issued.accessToken(),100L);});
            var second=workers.submit(()->{start.await();return controlRead(other.accessToken(),200L);});
            start.countDown();assertTrue(first.get(8,java.util.concurrent.TimeUnit.SECONDS));assertTrue(second.get(8,java.util.concurrent.TimeUnit.SECONDS));
        } finally {workers.shutdownNow();}
        try(var request=proof(issued.accessToken(),System.nanoTime()+8_000_000_000L)) {
            var failure=assertThrows(IntegrationReadException.class,()->proofReader(request).overview(
                    IntegrationInvocation.overview(request.authority(),System.nanoTime()+8_000_000_000L),100L,b->{
                        new AiTenantApplicationService(provider("tenant",7,71)).decide(approvalA.approvalId(),approvalA.rowVersion(),AiTenantApplicationService.Decision.REVOKE,java.util.Set.of(),0);
                        return b.size();
                    }));
            assertEquals(IntegrationAccessAudit.Outcome.DENIED,failure.outcome());
            try(var c=owner(db)) {
                assertEquals(1,count(c,"SELECT COUNT(*) FROM dbo.IntegrationAccessAuditLog WHERE InvocationId='"+request.authority().invocationId()+"' AND Outcome='ALLOWED_READ'"));
                assertEquals(1,count(c,"SELECT COUNT(*) FROM dbo.IntegrationAccessAuditLog WHERE InvocationId='"+request.authority().invocationId()+"' AND Stage='TERMINAL' AND Outcome='DENIED'"));
                assertEquals(1,count(c,"SELECT COUNT(*) FROM dbo.AiCimdInvocationV1 WHERE InvocationId='"+request.authority().invocationId()+"' AND HandoffConsumed=0"));
            }
        }
        assertThrows(SecurityException.class,()->proof(issued.accessToken(),System.nanoTime()+8_000_000_000L));
        assertTrue(controlRead(other.accessToken(),200L),"Revoking A must preserve B's live authority.");
        try(var request=proof(other.accessToken(),System.nanoTime()+8_000_000_000L)) {
            long until=System.nanoTime()+1_000_000_000L;
            var failure=assertThrows(IntegrationReadException.class,()->proofReader(request).overview(
                    IntegrationInvocation.overview(request.authority(),until),200L,b->{
                        try {new java.util.concurrent.CountDownLatch(1).await(Math.max(1,until-System.nanoTime()+1_000_000L),java.util.concurrent.TimeUnit.NANOSECONDS);}
                        catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new AssertionError(interrupted);}
                        return b.size();
                    }));
            assertEquals(IntegrationAccessAudit.Reason.DEADLINE,failure.reason());
            try(var c=owner(db)){assertEquals(1,count(c,"SELECT COUNT(*) FROM dbo.AiCimdInvocationV1 WHERE InvocationId='"+request.authority().invocationId()+"' AND ReadEventId IS NOT NULL AND HandoffConsumed=0"));}
        }
    }
    boolean controlRead(String token,long caseId)throws Exception {
        long began=System.nanoTime();
        try(var request=proof(token,began+8_000_000_000L)) {
            var result=proofReader(request).overview(IntegrationInvocation.overview(request.authority(),began+8_000_000_000L),caseId,DisclosureBuffer::size);
            assertTrue(System.nanoTime()-began<8_000_000_000L);
            System.err.println("PR4E concurrentProofControlMillis="+(System.nanoTime()-began)/1_000_000);
            return java.nio.charset.StandardCharsets.UTF_8.decode(result.bytes()).toString().contains("\"caseId\":"+caseId);
        }
    }

}
